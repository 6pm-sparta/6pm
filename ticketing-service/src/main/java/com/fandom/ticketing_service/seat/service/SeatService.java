package com.fandom.ticketing_service.seat.service;

import com.fandom.common.exception.CustomException;
import com.fandom.ticketing_service.common.exception.TicketingErrorCode;
import com.fandom.ticketing_service.order.client.OrderClient;
import com.fandom.ticketing_service.order.dto.CreateOrderRequest;
import com.fandom.ticketing_service.queue.service.PurchaseTokenService;
import com.fandom.ticketing_service.seat.domain.entity.ShowSeat;
import com.fandom.ticketing_service.seat.domain.repository.ShowSeatRepository;
import com.fandom.ticketing_service.domain.ShowSeat;
import com.fandom.ticketing_service.domain.ShowSeatRepository;
import com.fandom.ticketing_service.seat.dto.HoldResponse;
import com.fandom.ticketing_service.seat.dto.ShowSeatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private static final String SEAT_KEY = "show:%d:seat:%s";
    private static final String OWNER_KEY = "show:%d:seat:%s:owner";
    private static final String INVENTORY_KEY = "inventory:%d";
    private static final String PURCHASE_COUNT_KEY = "purchase-count:%s:%d";
    private static final int MAX_PER_USER = 2;

    // owner 값은 "{userId}:{status}" 형태. 주문 생성(orderClient.create) 도중엔 PENDING으로 두어
    // releaseHold가 끼어들어 DB에는 orderId가 박히는데 Redis는 이미 풀려버리는 레이스를 막는다.
    private static final String OWNER_STATUS_PENDING = "PENDING";
    private static final String OWNER_STATUS_CONFIRMED = "CONFIRMED";

    // Lua: (seatKey, inventoryKey, countKey, ownerKey) ARGV: maxPerUser, userId, status → 1:성공, 0:선점됨, -1:재고없음, -2:한도초과
    private static final RedisScript<Long> HOLD_SCRIPT = RedisScript.of("""
            local inv = tonumber(redis.call('GET', KEYS[2]) or '0')
            if inv <= 0 then return -1 end
            local cnt = tonumber(redis.call('GET', KEYS[3]) or '0')
            if cnt >= tonumber(ARGV[1]) then return -2 end
            local ok = redis.call('SET', KEYS[1], 'HOLDING', 'NX', 'EX', '600')
            if not ok then return 0 end
            redis.call('SET', KEYS[4], ARGV[2] .. ':' .. ARGV[3], 'EX', '600')
            redis.call('DECR', KEYS[2])
            redis.call('INCR', KEYS[3])
            return 1
            """, Long.class);

    // Lua: (ownerKey) ARGV: userId, status → owner의 상태를 TTL 유지한 채 갱신
    private static final RedisScript<Long> CONFIRM_OWNER_SCRIPT = RedisScript.of("""
            local owner = redis.call('GET', KEYS[1])
            if not owner then return 0 end
            redis.call('SET', KEYS[1], ARGV[1] .. ':' .. ARGV[2], 'KEEPTTL')
            return 1
            """, Long.class);

    // Lua: (ownerKey, seatKey, inventoryKey, countKey) ARGV: userId → 1:성공, -1:선점 없음/만료됨, -2:본인 선점 아님, -3:주문 생성 처리 중
    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            local owner = redis.call('GET', KEYS[1])
            if not owner then return -1 end
            local sep = string.find(owner, ':')
            local ownerId = string.sub(owner, 1, sep - 1)
            local status = string.sub(owner, sep + 1)
            if ownerId ~= ARGV[1] then return -2 end
            if status == 'PENDING' then return -3 end
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            redis.call('INCR', KEYS[3])
            redis.call('DECR', KEYS[4])
            return 1
            """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ShowSeatRepository showSeatRepository;
    private final OrderClient orderClient;
    private final PurchaseTokenService purchaseTokenService;

    public List<ShowSeatResponse> getSeats(Long showId) {
        List<ShowSeat> seats = showSeatRepository.findAllByShowId(showId);
        if (seats.isEmpty()) return List.of();

        List<String> keys = seats.stream()
                .map(seat -> SEAT_KEY.formatted(showId, seat.getId()))
                .toList();
        List<String> statuses = redisTemplate.opsForValue().multiGet(keys);

        return IntStream.range(0, seats.size())
                .mapToObj(i -> {
                    String status = (statuses != null && statuses.get(i) != null) ? statuses.get(i) : "AVAILABLE";
                    return ShowSeatResponse.of(seats.get(i), status);
                })
                .toList();
    }

    @Transactional
    public HoldResponse hold(UUID showSeatId, UUID userId) {
        ShowSeat seat = showSeatRepository.findById(showSeatId)
                .orElseThrow(() -> new CustomException(TicketingErrorCode.SEAT_NOT_FOUND));

        if (!purchaseTokenService.exists(seat.getShowId(), userId)) {
            throw new CustomException(TicketingErrorCode.PURCHASE_TOKEN_NOT_FOUND);
        }

        String seatKey = SEAT_KEY.formatted(seat.getShowId(), showSeatId);
        String ownerKey = OWNER_KEY.formatted(seat.getShowId(), showSeatId);
        String inventoryKey = INVENTORY_KEY.formatted(seat.getShowId());
        String countKey = PURCHASE_COUNT_KEY.formatted(userId, seat.getShowId());

        Long result = redisTemplate.execute(HOLD_SCRIPT,
                List.of(seatKey, inventoryKey, countKey, ownerKey),
                String.valueOf(MAX_PER_USER), userId.toString(), OWNER_STATUS_PENDING);

        switch ((result != null ? result.intValue() : 0)) {
            case 1 -> { /* 선점 성공 */ }
            case 0 -> throw new CustomException(TicketingErrorCode.SEAT_ALREADY_HELD);
            case -1 -> throw new CustomException(TicketingErrorCode.NO_INVENTORY);
            case -2 -> throw new CustomException(TicketingErrorCode.PURCHASE_LIMIT_EXCEEDED);
            default -> throw new CustomException(TicketingErrorCode.SEAT_ALREADY_HELD);
        }

        try {
            var order = orderClient.create(new CreateOrderRequest(userId, seat.getShowId(), showSeatId, seat.getPrice()));
            seat.assignOrder(order.orderId());
            showSeatRepository.save(seat);
            redisTemplate.execute(CONFIRM_OWNER_SCRIPT, List.of(ownerKey), userId.toString(), OWNER_STATUS_CONFIRMED);
            return new HoldResponse(order.orderId());
        } catch (Exception e) {
            // 주문 생성 실패 시 선점 해제
            redisTemplate.delete(seatKey);
            redisTemplate.delete(ownerKey);
            redisTemplate.opsForValue().increment(inventoryKey);
            redisTemplate.opsForValue().decrement(countKey);
            log.error("주문 생성 실패, 선점 롤백: seatId={}, userId={}", showSeatId, userId);
            throw new CustomException(TicketingErrorCode.ORDER_CREATE_FAILED);
        }
    }

    @Transactional
    public void releaseHold(UUID showSeatId, UUID userId) {
        ShowSeat seat = showSeatRepository.findById(showSeatId)
                .orElseThrow(() -> new CustomException(TicketingErrorCode.SEAT_NOT_FOUND));

        String ownerKey = OWNER_KEY.formatted(seat.getShowId(), showSeatId);
        String seatKey = SEAT_KEY.formatted(seat.getShowId(), showSeatId);
        String inventoryKey = INVENTORY_KEY.formatted(seat.getShowId());
        String countKey = PURCHASE_COUNT_KEY.formatted(userId, seat.getShowId());

        Long result = redisTemplate.execute(RELEASE_SCRIPT,
                List.of(ownerKey, seatKey, inventoryKey, countKey),
                userId.toString());

        switch (result != null ? result.intValue() : -1) {
            case 1 -> { /* 해제 성공 */ }
            case -2 -> throw new CustomException(TicketingErrorCode.SEAT_HOLD_FORBIDDEN);
            case -3 -> throw new CustomException(TicketingErrorCode.SEAT_HOLD_PROCESSING);
            default -> throw new CustomException(TicketingErrorCode.SEAT_NOT_HELD);
        }

        seat.releaseOrder();
        log.info("좌석 선점 해제: seatId={}, userId={}", showSeatId, userId);
    }

    // seatKey가 DEL이 아닌 TTL 만료로 사라졌을 때만 호출됨(SeatHoldExpirationListener) → 결제 미완료 상태로 방치된 선점만 해제 대상
    @Transactional
    public void releaseExpiredHold(Long showId, UUID showSeatId) {
        showSeatRepository.findById(showSeatId).ifPresentOrElse(seat -> {
            if (seat.getOrderId() == null) {
                return;
            }

            seat.releaseOrder();
            redisTemplate.opsForValue().increment(INVENTORY_KEY.formatted(showId));
            log.info("좌석 선점 만료, 자동 해제: showId={}, seatId={}", showId, showSeatId);
        }, () -> log.warn("만료된 선점 좌석을 찾을 수 없음: showId={}, seatId={}", showId, showSeatId));
    }
}

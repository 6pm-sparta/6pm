package com.fandom.ticketing_service.seat.application;

import com.fandom.common.exception.CustomException;
import com.fandom.ticketing_service.common.exception.TicketingErrorCode;
import com.fandom.ticketing_service.kafka.event.SeatBookFailedEvent;
import com.fandom.ticketing_service.kafka.event.SeatBookedEvent;
import com.fandom.ticketing_service.kafka.producer.SeatEventProducer;
import com.fandom.ticketing_service.seat.domain.entity.ShowSeat;
import com.fandom.ticketing_service.seat.domain.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatConfirmService {

    private static final String SEAT_KEY = "show:%s:seat:%s";
    private static final String OWNER_KEY = "show:%s:seat:%s:owner";
    private static final String INVENTORY_KEY = "inventory:%s";

    // KEYS: 1=seatKey 2=ownerKey 3=inventoryKey  ARGV: 1=showId(purchase-count 키 조립용)
    // GET->DELETE->INCR->DECR을 한 번에 원자적으로 처리해, 같은 orderId로 release가 중복 호출돼도
    // (Kafka at-least-once 재전송 등) 재고/구매수 증감이 두 번 일어나지 않게 한다.
    // owner가 이미 없으면(먼저 처리된 중복 호출) inventory/purchase-count 둘 다 손대지 않고 seat/owner 키 삭제만 멱등하게 수행한다.
    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            local owner = redis.call('GET', KEYS[2])
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            if owner then
                redis.call('INCR', KEYS[3])
                local sep = string.find(owner, ':')
                local userId = string.sub(owner, 1, sep - 1)
                redis.call('DECR', 'purchase-count:' .. userId .. ':' .. ARGV[1])
                return 1
            end
            return 0
            """, Long.class);

    private final ShowSeatRepository showSeatRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SeatEventProducer seatEventProducer;

    @Transactional
    public void confirmSeat(UUID orderId) {
        ShowSeat seat;
        try {
            seat = showSeatRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new CustomException(TicketingErrorCode.SEAT_NOT_FOUND));
        } catch (CustomException e) {
            // 좌석 자체가 없는 경우라 seatId를 채울 수 없음 → null이 정상값
            log.error("좌석 확정 실패: orderId={}, reason={}", orderId, e.getMessage());
            seatEventProducer.publishSeatBookFailed(new SeatBookFailedEvent(orderId, null, e.getMessage()));
            throw e;
        }

        UUID seatId = seat.getId();
        try {
            // BOOKED로 SET(TTL 없이 영구 유지) — DELETE하면 getSeats()가 기본값 "AVAILABLE"로 반환해
            // 이미 팔린 좌석이 다시 선점 가능한 것처럼 보이고, hold()가 SETNX만으로 선점을 허용해버림
            redisTemplate.opsForValue().set(SEAT_KEY.formatted(seat.getShowId(), seatId), "BOOKED");
            redisTemplate.delete(OWNER_KEY.formatted(seat.getShowId(), seatId));
            seatEventProducer.publishSeatBooked(new SeatBookedEvent(orderId, seatId));
        } catch (Exception e) {
            log.error("좌석 확정 실패: orderId={}, seatId={}, reason={}", orderId, seatId, e.getMessage());
            seatEventProducer.publishSeatBookFailed(new SeatBookFailedEvent(orderId, seatId, e.getMessage()));
        }
    }

    @Transactional
    public void releaseSeat(UUID orderId) {
        showSeatRepository.findByOrderId(orderId).ifPresentOrElse(seat -> {
            String seatKey = SEAT_KEY.formatted(seat.getShowId(), seat.getId());
            String ownerKey = OWNER_KEY.formatted(seat.getShowId(), seat.getId());
            String inventoryKey = INVENTORY_KEY.formatted(seat.getShowId());

            seat.releaseOrder();
            Long result = redisTemplate.execute(RELEASE_SCRIPT,
                    List.of(seatKey, ownerKey, inventoryKey), seat.getShowId().toString());

            if (result != null && result == 1) {
                log.info("좌석 해제 완료: orderId={}, seatId={}", orderId, seat.getId());
            } else {
                log.info("좌석 해제 스킵(이미 처리된 중복 호출): orderId={}, seatId={}", orderId, seat.getId());
            }
        }, () -> log.warn("해제할 좌석 없음: orderId={}", orderId));
    }
}

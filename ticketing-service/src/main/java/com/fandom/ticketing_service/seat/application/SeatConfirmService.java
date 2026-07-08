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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatConfirmService {

    private static final String SEAT_KEY = "show:%s:seat:%s";
    private static final String OWNER_KEY = "show:%s:seat:%s:owner";
    private static final String INVENTORY_KEY = "inventory:%s";
    private static final String PURCHASE_COUNT_KEY = "purchase-count:%s:%s";

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

            // owner가 이미 없으면(releaseHold가 먼저 처리한 경우) purchase-count도 이미 감소했으므로 중복 감소하지 않는다
            String owner = redisTemplate.opsForValue().get(ownerKey);

            seat.releaseOrder();
            redisTemplate.delete(seatKey);
            redisTemplate.delete(ownerKey);
            redisTemplate.opsForValue().increment(inventoryKey);
            if (owner != null) {
                String userId = owner.substring(0, owner.indexOf(':'));
                redisTemplate.opsForValue().decrement(PURCHASE_COUNT_KEY.formatted(userId, seat.getShowId()));
            }

            log.info("좌석 해제 완료: orderId={}, seatId={}", orderId, seat.getId());
        }, () -> log.warn("해제할 좌석 없음: orderId={}", orderId));
    }
}

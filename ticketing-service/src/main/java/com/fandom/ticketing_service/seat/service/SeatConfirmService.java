package com.fandom.ticketing_service.seat.service;

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

    private static final String SEAT_KEY = "show:%d:seat:%s";
    private static final String OWNER_KEY = "show:%d:seat:%s:owner";
    private static final String INVENTORY_KEY = "inventory:%d";

    private final ShowSeatRepository showSeatRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SeatEventProducer seatEventProducer;

    @Transactional
    public void confirmSeat(UUID orderId) {
        UUID seatId = null;
        try {
            ShowSeat seat = showSeatRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new CustomException(TicketingErrorCode.SEAT_NOT_FOUND));
            seatId = seat.getId();
            redisTemplate.delete(SEAT_KEY.formatted(seat.getShowId(), seatId));
            redisTemplate.delete(OWNER_KEY.formatted(seat.getShowId(), seatId));
            seatEventProducer.publishSeatBooked(new SeatBookedEvent(orderId, seatId));
        } catch (Exception e) {
            log.error("좌석 확정 실패: orderId={}, reason={}", orderId, e.getMessage());
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
            redisTemplate.delete(seatKey);
            redisTemplate.delete(ownerKey);
            redisTemplate.opsForValue().increment(inventoryKey);

            log.info("좌석 해제 완료: orderId={}, seatId={}", orderId, seat.getId());
        }, () -> log.warn("해제할 좌석 없음: orderId={}", orderId));
    }
}

package com.fandom.order_service.order.application.compensation;

import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * ticketing.seat.book.failed 수신 → SAGA 보상 트랜잭션(SeatEventConsumer가 이 서비스를 호출).
 *
 * 환불이 webhook으로 REFUND_FAILED 응답을 받으면 그 시점에 바로 FAILED로 전이된다.
 * (order.status 기준 FAILED. payment.paymentStatus는 REFUND_FAILED로 별도 전이.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCompensationService {

    private final OrderCompensationWriter orderCompensationWriter;
    private final PaymentGateway paymentGateway;

    public void compensate(UUID orderId, String reason) {

        OrderCompensationResult started = orderCompensationWriter.startCompensation(orderId, reason);

        if (started.type() == OrderCompensationResult.Type.ALREADY_HANDLED) {
            log.info("[SAGA 보상] 이미 처리된 주문 - 이벤트 무시. orderId={}, status={}",
                    started.orderId(), started.status());
            return;
        }

        if (started.type() == OrderCompensationResult.Type.SKIPPED_INVALID_STATE) {
            log.warn("[SAGA 보상] CANCEL_REQUESTED로 전이할 수 없는 상태 - 이벤트 스킵. orderId={}, status={}",
                    started.orderId(), started.status());
            return;
        }

        Payment payment = started.paymentToRefund();

        try {
            paymentGateway.requestRefundAsync(orderId, payment.getPgTransactionId(), payment.getAmount());
            log.info("[SAGA 보상] PG 환불 요청 접수 완료, 결과는 webhook으로 비동기 반영됨. orderId={}, pgTransactionId={}",
                    orderId, payment.getPgTransactionId());
        } catch (RuntimeException pgFailure) {

            // PG 접수 자체가 실패(네트워크 오류 등, webhook이 영영 오지 않을 상황) — 복구는 별도 배치 이슈 스코프.
            log.error("[SAGA 보상] PG 환불 접수 실패. orderId={}, paymentId={}, pgTransactionId={}",
                    orderId, payment.getId(), payment.getPgTransactionId(), pgFailure);
        }
    }
}

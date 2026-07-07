package com.fandom.order_service.payment.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.order_service.payment.application.request.PaymentRequestResult;
import com.fandom.order_service.payment.application.request.PaymentRequestService;
import com.fandom.order_service.payment.application.query.PaymentQueryService;
import com.fandom.order_service.payment.presentation.dto.request.PaymentRequest;
import com.fandom.order_service.payment.presentation.dto.response.PaymentDetailResponse;
import com.fandom.order_service.payment.presentation.dto.response.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * 결제 API. (외부 - Gateway 경유)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentRequestService paymentRequestService;
    private final PaymentQueryService paymentQueryService;

    /**
     * 결제 요청. 신규 처리면 201, 동일 Idempotency-Key로 인한 멱등 응답이면 200을 반환한다.
     */
    @PostMapping("/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> requestPayment(
            @Valid @RequestBody PaymentRequest request,
            @CurrentIdCard UserIdCard idCard,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        PaymentRequestResult result = paymentRequestService.requestPayment(request, idCard.getUserId(), idempotencyKey);

        if (result.newlyProcessed()) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.created(result.payment()));
        }

        return ResponseEntity.ok(ApiResponse.success(result.payment()));
    }

    /**
     * 결제 시도 단건 상세 조회. 본인 주문의 결제가 아니면 403, 존재하지 않으면 404.
     */
    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentDetailResponse> getPayment(
            @PathVariable("paymentId") UUID paymentId,
            @CurrentIdCard UserIdCard idCard) {

        PaymentDetailResponse response = paymentQueryService.getPayment(paymentId, idCard.getUserId());

        return ApiResponse.success(response);
    }

    /**
     * 주문의 결제 시도 전체 목록 조회(최신 시도 먼저). 결제 시도가 0건이어도 200 + 빈 배열을 반환한다
     * (아직 결제 전인 PENDING 주문 등 정상 케이스). 페이징 없음 — 재시도 횟수 자체가 제한적이라 무한정
     * 쌓이지 않는다.
     */
    @GetMapping("/orders/{orderId}/payments")
    public ApiResponse<List<PaymentDetailResponse>> getPaymentsByOrder(
            @PathVariable("orderId") UUID orderId,
            @CurrentIdCard UserIdCard idCard) {

        List<PaymentDetailResponse> response = paymentQueryService.getPaymentsByOrder(orderId, idCard.getUserId());

        return ApiResponse.success(response);
    }

}
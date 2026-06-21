package com.fandom.order_service.payment.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.order_service.payment.application.PaymentRequestResult;
import com.fandom.order_service.payment.application.PaymentRequestService;
import com.fandom.order_service.payment.presentation.dto.request.PaymentRequest;
import com.fandom.order_service.payment.presentation.dto.response.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 API. (외부 - Gateway 경유)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentRequestService paymentRequestService;

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
}
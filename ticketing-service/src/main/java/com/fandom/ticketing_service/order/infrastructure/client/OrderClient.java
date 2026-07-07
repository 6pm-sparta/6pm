package com.fandom.ticketing_service.order.infrastructure.client;

import com.fandom.common.dto.ApiResponse;
import com.fandom.ticketing_service.order.infrastructure.dto.CreateOrderRequest;
import com.fandom.ticketing_service.order.infrastructure.dto.CreateOrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "order-service", path = "/internal/v1/orders")
public interface OrderClient {

    @PostMapping
    ApiResponse<CreateOrderResponse> create(@RequestBody CreateOrderRequest request);

    @DeleteMapping("/{orderId}")
    ApiResponse<Void> cancel(@PathVariable UUID orderId, @RequestParam UUID requesterId);
}

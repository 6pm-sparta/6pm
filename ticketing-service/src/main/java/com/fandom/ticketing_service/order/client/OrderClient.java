package com.fandom.ticketing_service.order.client;

import com.fandom.ticketing_service.order.dto.CreateOrderRequest;
import com.fandom.ticketing_service.order.dto.CreateOrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderClient {

    @PostMapping
    CreateOrderResponse create(@RequestBody CreateOrderRequest request);
}

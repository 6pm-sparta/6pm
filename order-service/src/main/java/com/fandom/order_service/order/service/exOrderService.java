package com.fandom.order_service.order.service;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.dto.CreateOrderRequest;
import com.fandom.order_service.order.dto.CreateOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public CreateOrderResponse create(CreateOrderRequest request) {
        Order order = Order.builder()
                .userId(request.userId())
                .showId(request.showId())
                .showSeatId(request.showSeatId())
                .price(request.price())
                .build();
        orderRepository.save(order);
        return new CreateOrderResponse(order.getId());
    }
}

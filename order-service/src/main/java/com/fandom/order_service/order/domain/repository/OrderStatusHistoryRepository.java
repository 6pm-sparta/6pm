package com.fandom.order_service.order.domain.repository;

import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
}

package com.fandom.order_service.order.application.creation;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * orders INSERT + order_status_histories INSERT를 하나의 트랜잭션으로 묶는다 — API 명세서 "트랜잭션 범위" 항목.
 *
 * OrderCreationService와 별도 빈으로 분리한 이유(중요):
 * PostgreSQ 상태로 만든다. 같은 트랜잭L은 UNIQUE 제약 위반(uq_orders_seat_active) 같은 에러가 나면 트랜잭션 전체를
 * "aborted" 상태로 만든다. 같은 트랜잭션 안에서 그 이후 SELECT를 또 날리면
 * "current transaction is aborted" 에러가 추가로 난다.
 * 그래서 INSERT 실패 시 "이미 있는 진행중 주문을 찾아서 멱등 응답으로 돌려주는" 폴백 조회는
 * 반드시 이 트랜잭션이 완전히 끝난(롤백된) 뒤, 새 트랜잭션에서 실행해야 한다.
 * 같은 클래스 안에서 @Transactional 메서드를 this.method()로 호출하면 Spring AOP 프록시를
 * 거치지 않아 트랜잭션 경계가 분리되지 않으므로, 호출하는 쪽(OrderCreationService)과
 * 물리적으로 다른 스프링 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class OrderCreationWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Transactional
    public Order insertPendingOrder(UUID seatId, UUID userId, Long totalAmount, LocalDateTime expiredAt) {

        // 주문 생성
        Order order = orderRepository.save(Order.createPending(seatId, userId, totalAmount, expiredAt));

        // 최초 상태 이력 생성
        orderStatusHistoryRepository.save(
                OrderStatusHistory.initial(order.getId(), OrderStatus.PENDING, "주문 생성")
        );

        return order;
    }
}

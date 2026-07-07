package com.fandom.order_service.kafka.producer;

import com.fandom.order_service.kafka.KafkaTopics;
import com.fandom.order_service.kafka.event.NotificationSendEvent;
import com.fandom.order_service.kafka.event.PaymentCancelledEvent;
import com.fandom.order_service.kafka.event.PaymentCompletedEvent;
import com.fandom.order_service.kafka.event.PaymentFailedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventProducer 단위 테스트")
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderEventProducer orderEventProducer;

    private UUID orderId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderEventProducer = new OrderEventProducer(kafkaTemplate);
        orderId = UUID.randomUUID();

        // send()는 항상 CompletableFuture를 리턴하는데(OrderEventProducer가 whenComplete로
        // 체이닝함), Mockito 기본 stub은 null을 리턴해서 NPE가 난다. 성공 케이스로 통일 stub.
        // SendResult/RecordMetadata는 직접 생성자로 안 만들고 mock으로 — kafka-clients 버전마다
        // RecordMetadata 생성자 시그니처가 달라서 직접 new 하면 버전에 취약해진다.
        // lenient()로 두는 이유: 실패 케이스 2개 테스트가 이 stub을 자체 stub으로 덮어써서
        // 실제로는 안 쓰이는데, strict stub 모드에서 UnnecessaryStubbingException이 나는 걸 방지.
        SendResult<String, Object> sendResult = mock(SendResult.class);
        lenient().when(sendResult.getRecordMetadata()).thenReturn(mock(RecordMetadata.class));
        lenient().when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Test
    @DisplayName("publishPaymentCompleted는 order.payment.completed 토픽에 orderId를 key로 발행한다")
    void publishPaymentCompleted_sendsToCorrectTopic() {
        // when
        orderEventProducer.publishPaymentCompleted(orderId);

        // then
        verify(kafkaTemplate).send(KafkaTopics.PAYMENT_COMPLETED, orderId.toString(), new PaymentCompletedEvent(orderId));
    }

    @Test
    @DisplayName("publishPaymentFailed는 order.payment.failed 토픽에 발행한다")
    void publishPaymentFailed_sendsToCorrectTopic() {
        // when
        orderEventProducer.publishPaymentFailed(orderId);

        // then
        verify(kafkaTemplate).send(KafkaTopics.PAYMENT_FAILED, orderId.toString(), new PaymentFailedEvent(orderId));
    }

    @Test
    @DisplayName("publishPaymentCancelled는 order.payment.cancelled 토픽에 발행한다")
    void publishPaymentCancelled_sendsToCorrectTopic() {
        // when
        orderEventProducer.publishPaymentCancelled(orderId);

        // then
        verify(kafkaTemplate).send(KafkaTopics.PAYMENT_CANCELLED, orderId.toString(), new PaymentCancelledEvent(orderId));
    }

    @Test
    @DisplayName("publishOrderCompletedNotification은 notification.send 토픽에 type=ORDER_COMPLETED로 발행한다")
    void publishOrderCompletedNotification_sendsToCorrectTopic() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        orderEventProducer.publishOrderCompletedNotification(orderId, userId);

        // then
        verify(kafkaTemplate).send(KafkaTopics.NOTIFICATION_SEND, orderId.toString(),
                new NotificationSendEvent(orderId, "ORDER_COMPLETED",
                        "예매가 확정되었습니다", "주문하신 예매가 확정되었습니다.", List.of(userId)));
    }

    @Test
    @DisplayName("publishOrderCancelledNotification은 notification.send 토픽에 type=ORDER_CANCELED로 발행한다")
    void publishOrderCancelledNotification_sendsToCorrectTopic() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        orderEventProducer.publishOrderCancelledNotification(orderId, userId);

        // then
        verify(kafkaTemplate).send(KafkaTopics.NOTIFICATION_SEND, orderId.toString(),
                new NotificationSendEvent(orderId, "ORDER_CANCELED",
                        "주문이 취소되었습니다", "환불이 완료되었습니다.", List.of(userId)));
    }

    @Test
    @DisplayName("send()가 실패 Future를 반환해도 예외를 던지지 않는다(비즈니스 흐름을 막지 않음)")
    void send_futureFails_doesNotThrow() {
        // given
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka 장애 시뮬레이션"));
        given(kafkaTemplate.send(any(String.class), any(String.class), any())).willReturn(failedFuture);

        // when & then (예외가 안 던져지면 통과)
        orderEventProducer.publishPaymentCompleted(orderId);
    }

    @Test
    @DisplayName("send() 호출 자체가 동기적으로 예외를 던져도 전파하지 않는다")
    void send_throwsSynchronously_doesNotPropagate() {
        // given
        given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .willThrow(new RuntimeException("max.block.ms 초과 시뮬레이션"));

        // when & then (예외가 안 던져지면 통과)
        orderEventProducer.publishPaymentCompleted(orderId);
    }
}
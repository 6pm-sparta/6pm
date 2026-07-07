package com.fandom.order_service.payment.application.zombierecovery;

import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ZombiePaymentRecoveryScheduler 단위 테스트")
class ZombiePaymentRecoverySchedulerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ZombiePaymentRecoveryWriter zombiePaymentRecoveryWriter;

    private ZombiePaymentRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ZombiePaymentRecoveryScheduler(paymentRepository, zombiePaymentRecoveryWriter, 100);
    }

    @Test
    @DisplayName("좀비 후보가 없으면 Writer를 호출하지 않는다")
    void recoverZombiePayments_noCandidates_doesNothing() {
        // given
        given(paymentRepository.findZombiePaymentOrderIds(any(), any(), any(), any())).willReturn(List.of());

        // when
        scheduler.recoverZombiePayments();

        // then
        verify(zombiePaymentRecoveryWriter, never()).recover(any());
    }

    @Test
    @DisplayName("후보 전부에 대해 건별로 Writer.recover를 호출한다")
    void recoverZombiePayments_callsWriterPerCandidate() {
        // given
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        given(paymentRepository.findZombiePaymentOrderIds(any(), any(), any(), any()))
                .willReturn(List.of(orderId1, orderId2));
        given(zombiePaymentRecoveryWriter.recover(orderId1)).willReturn(ZombiePaymentRecoveryResult.APPROVED_SYNCED);
        given(zombiePaymentRecoveryWriter.recover(orderId2)).willReturn(ZombiePaymentRecoveryResult.FAILED_SYNCED);

        // when
        scheduler.recoverZombiePayments();

        // then
        verify(zombiePaymentRecoveryWriter).recover(orderId1);
        verify(zombiePaymentRecoveryWriter).recover(orderId2);
    }

    @Test
    @DisplayName("한 건에서 예외가 발생해도 나머지 건을 계속 처리한다")
    void recoverZombiePayments_oneThrows_continuesWithRest() {
        // given
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        given(paymentRepository.findZombiePaymentOrderIds(any(), any(), any(), any()))
                .willReturn(List.of(orderId1, orderId2));
        given(zombiePaymentRecoveryWriter.recover(orderId1)).willThrow(new RuntimeException("DB 오류"));
        given(zombiePaymentRecoveryWriter.recover(orderId2)).willReturn(ZombiePaymentRecoveryResult.SKIPPED);

        // when
        scheduler.recoverZombiePayments();

        // then — orderId1 실패해도 orderId2는 처리됨
        verify(zombiePaymentRecoveryWriter, times(2)).recover(any());
    }
}

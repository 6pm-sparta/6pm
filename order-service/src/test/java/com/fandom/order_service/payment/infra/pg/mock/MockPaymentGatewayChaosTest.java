package com.fandom.order_service.payment.infra.pg.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 확률적 장애 주입 모드의 누적 확률 구간 매핑을 검증한다.
 * roll을 double(0.0~1.0)이 아닌 int(0~99)로 받는다 — double 누적 합산은 부동소수점 오차로
 * 경계값(예: 0.03+0.02+0.10 != 0.15)이 어긋날 수 있어 정수 비교로 바꿈.
 * ThreadLocalRandom 시드에 기대지 않도록 resolveChaosOutcome을 순수 함수로 분리해 경계값만 확인한다.
 */
@DisplayName("MockPaymentGateway 확률적 장애 주입 - 경계값")
class MockPaymentGatewayChaosTest {

    // 비율: FAILED 3 / LOST 2(누적 5) / SLOW 10(누적 15) / 나머지(15~99) NORMAL

    @Test
    @DisplayName("0 ~ 2는 FAILED")
    void resolveChaosOutcome_failedRange() {
        assertThat(MockPaymentGateway.resolveChaosOutcome(0))
                .isEqualTo(MockPaymentGateway.ChaosOutcome.FAILED);
        assertThat(MockPaymentGateway.resolveChaosOutcome(2))
                .isEqualTo(MockPaymentGateway.ChaosOutcome.FAILED);
    }

    @Test
    @DisplayName("3 ~ 4는 LOST")
    void resolveChaosOutcome_lostRange() {
        assertThat(MockPaymentGateway.resolveChaosOutcome(3))
                .isEqualTo(MockPaymentGateway.ChaosOutcome.LOST);
        assertThat(MockPaymentGateway.resolveChaosOutcome(4))
                .isEqualTo(MockPaymentGateway.ChaosOutcome.LOST);
    }

    @Test
    @DisplayName("5 ~ 14는 SLOW")
    void resolveChaosOutcome_slowRange() {
        assertThat(MockPaymentGateway.resolveChaosOutcome(5))
                .isEqualTo(MockPaymentGateway.ChaosOutcome.SLOW);
        assertThat(MockPaymentGateway.resolveChaosOutcome(14))
                .isEqualTo(MockPaymentGateway.ChaosOutcome.SLOW);
    }

    @Test
    @DisplayName("15 ~ 99는 NORMAL")
    void resolveChaosOutcome_normalRange() {
        assertThat(MockPaymentGateway.resolveChaosOutcome(15))
                .isEqualTo(MockPaymentGateway.ChaosOutcome.NORMAL);
        assertThat(MockPaymentGateway.resolveChaosOutcome(99))
                .isEqualTo(MockPaymentGateway.ChaosOutcome.NORMAL);
    }
}

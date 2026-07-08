package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.order_service.config.ChaosProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockPgChaosPolicy(#236) 단위 테스트.
 *
 * - decide(): enabled=false / hasMarker=true일 때 "항상 개입 안 함(NORMAL)"을 보장하는지 검증.
 *   (설정이 외부화되기 전에는 CHAOS_MODE_ENABLED가 컴파일 타임 상수라 이 검증 자체가 불가능했음)
 * - resolve(): 누적 확률 구간 매핑을 순수 함수로 검증. int 비교라 부동소수점 오차가 없다.
 */
@DisplayName("MockPgChaosPolicy - 확률적 장애 주입 정책")
class MockPgChaosPolicyTest {

    private static final int FAILED_PERCENT = 3;
    private static final int LOST_PERCENT = 2;
    private static final int SLOW_PERCENT = 10;

    @Test
    @DisplayName("enabled=false면 마커가 없어도 항상 NORMAL이다 (의도치 않은 장애 주입 방지)")
    void decide_disabled_alwaysNormal() {
        MockPgChaosPolicy policy = new MockPgChaosPolicy(
                new ChaosProperties(false, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT, 1000, 3000));

        for (int i = 0; i < 100; i++) {
            assertThat(policy.decide(false)).isEqualTo(MockPgChaosPolicy.Outcome.NORMAL);
        }
    }

    @Test
    @DisplayName("enabled=true여도 마커가 있으면 항상 NORMAL이다 (마커가 확률보다 우선)")
    void decide_hasMarker_alwaysNormal() {
        MockPgChaosPolicy policy = new MockPgChaosPolicy(
                new ChaosProperties(true, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT, 1000, 3000));

        for (int i = 0; i < 100; i++) {
            assertThat(policy.decide(true)).isEqualTo(MockPgChaosPolicy.Outcome.NORMAL);
        }
    }

    @Test
    @DisplayName("0 ~ (failedPercent - 1)은 FAILED")
    void resolve_failedRange() {
        assertThat(MockPgChaosPolicy.resolve(0, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT))
                .isEqualTo(MockPgChaosPolicy.Outcome.FAILED);
        assertThat(MockPgChaosPolicy.resolve(2, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT))
                .isEqualTo(MockPgChaosPolicy.Outcome.FAILED);
    }

    @Test
    @DisplayName("failedPercent ~ (failedPercent+lostPercent - 1)은 LOST")
    void resolve_lostRange() {
        assertThat(MockPgChaosPolicy.resolve(3, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT))
                .isEqualTo(MockPgChaosPolicy.Outcome.LOST);
        assertThat(MockPgChaosPolicy.resolve(4, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT))
                .isEqualTo(MockPgChaosPolicy.Outcome.LOST);
    }

    @Test
    @DisplayName("누적 5 ~ 14는 SLOW")
    void resolve_slowRange() {
        assertThat(MockPgChaosPolicy.resolve(5, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT))
                .isEqualTo(MockPgChaosPolicy.Outcome.SLOW);
        assertThat(MockPgChaosPolicy.resolve(14, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT))
                .isEqualTo(MockPgChaosPolicy.Outcome.SLOW);
    }

    @Test
    @DisplayName("나머지(15~99)는 NORMAL")
    void resolve_normalRange() {
        assertThat(MockPgChaosPolicy.resolve(15, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT))
                .isEqualTo(MockPgChaosPolicy.Outcome.NORMAL);
        assertThat(MockPgChaosPolicy.resolve(99, FAILED_PERCENT, LOST_PERCENT, SLOW_PERCENT))
                .isEqualTo(MockPgChaosPolicy.Outcome.NORMAL);
    }
}

package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.order_service.config.ChaosProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 부하 테스트용 확률적 장애 주입 정책.
 * 마커(FAIL_/TIMEOUT_ 등) 없는 요청에 한해 ChaosProperties 설정값에 따라 결과를 확률적으로 결정한다.
 */
@Component
@RequiredArgsConstructor
public class MockPgChaosPolicy {

    private final ChaosProperties properties;

    public enum Outcome { NORMAL, SLOW, FAILED, LOST }

    /** 마커 있는 요청이거나 확률모드가 꺼져 있으면 항상 NORMAL(개입 안 함). */
    public Outcome decide(boolean hasMarker) {

        if (hasMarker || !properties.enabled()) {
            return Outcome.NORMAL;
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        return resolve(roll, properties.failedPercent(), properties.lostPercent(), properties.slowPercent());
    }

    /**
     * roll(0~99)을 누적 확률 구간에 매핑한다. 정수 비교라 부동소수점 오차가 없다.
     * 순수 함수로 분리해서 랜덤 시드 없이 경계값 테스트가 가능하도록 함.
     */
    static Outcome resolve(int roll, int failedPercent, int lostPercent, int slowPercent) {

        int failedBoundary = failedPercent;
        int lostBoundary = failedBoundary + lostPercent;
        int slowBoundary = lostBoundary + slowPercent;

        if (roll < failedBoundary) {
            return Outcome.FAILED;
        }
        if (roll < lostBoundary) {
            return Outcome.LOST;
        }
        if (roll < slowBoundary) {
            return Outcome.SLOW;
        }
        return Outcome.NORMAL;
    }

    public long randomJitterMillis() {
        // jitterMaxMillis 값도 나오게 하려고 +1 (nextLong의 두 번째 인자는 포함 안 됨)
        return ThreadLocalRandom.current().nextLong(properties.jitterMinMillis(), properties.jitterMaxMillis() + 1);
    }
}

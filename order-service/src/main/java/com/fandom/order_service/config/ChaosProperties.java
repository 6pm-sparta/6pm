package com.fandom.order_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 부하 테스트용 확률적 장애 주입 모드 설정.
 *
 * enabled: 확률 모드 on/off. 마커(FAIL_/TIMEOUT_ 등) 없는 요청에만 적용.
 * failedPercent/lostPercent/slowPercent: 누적 퍼센트(정수, 0~100). 나머지는 정상 처리.
 * jitterMinMillis/jitterMaxMillis: SLOW 판정 시 기본 지연 위에 추가로 얹는 지연 범위(ms).
 */
@ConfigurationProperties(prefix = "order.chaos")
public record ChaosProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("3") int failedPercent,
        @DefaultValue("2") int lostPercent,
        @DefaultValue("10") int slowPercent,
        @DefaultValue("1000") long jitterMinMillis,
        @DefaultValue("3000") long jitterMaxMillis
) {
}

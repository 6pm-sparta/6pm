package com.fandom.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

/**
 * MockPaymentGateway의 비동기 webhook 콜백 전송에 필요한 빈.
 *
 * ThreadPoolTaskScheduler: 인위적 지연(order.pg-webhook.callback-delay-millis) 뒤 콜백을 "예약"하기
 * 위함. Thread.sleep으로 스레드를 블로킹하는 대신 schedule()로 위임해 스레드를 점유하지 않는다.
 */
@Configuration
public class PgWebhookCallbackConfig {

    @Bean
    public ThreadPoolTaskScheduler pgWebhookCallbackScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("pg-webhook-callback-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public RestClient pgWebhookRestClient() {
        return RestClient.create();
    }
}

package com.fandom.aiops_service.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final String SYSTEM_PROMPT = """
            너는 6pm 플랫폼의 SRE/AIOps 분석가다. Prometheus/Alertmanager가 보낸 장애 알림을 받아
            현업 엔지니어가 바로 활용할 수 있는 한국어 인시던트 리포트를 만든다.

            규칙:
            - 제공된 알림 데이터(알림명, severity, 서비스, 원본 payload)에 근거해서만 분석한다. 사실을 지어내지 않는다.
            - 정보가 부족하면 "추가 확인 필요" 형태로 솔직히 명시한다.
            - 각 항목은 군더더기 없이 핵심만. summary 1~2문장, rootCause 1~3문장, guide 는 실행 가능한 조치 위주.
            - 과장/추측성 단정 금지. 가설은 가설로 표현한다.
            """;

    @Bean
    public ChatClient incidentAnalysisChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }
}

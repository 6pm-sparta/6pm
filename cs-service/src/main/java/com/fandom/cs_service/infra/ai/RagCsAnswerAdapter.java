package com.fandom.cs_service.infra.ai;

import com.fandom.cs_service.application.port.CsAnswerPort;
import com.fandom.cs_service.application.port.CsVectorStorePort;
import com.fandom.cs_service.domain.exception.CsErrorCode;
import com.fandom.common.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "cs.rag.enabled", havingValue = "true")
public class RagCsAnswerAdapter implements CsAnswerPort {

    private static final int TOP_K = 4;

    private static final String SYSTEM_PROMPT = """
            당신은 크리에이터와 팬이 피드·실시간 채팅으로 소통하고 콘서트 티켓을 선착순 예매하는 팬덤 SNS 서비스의 고객센터 AI 상담원입니다.
            아래 [참고 문서]의 내용만 근거로 한국어로 정확하고 간결하게 답변하세요.
            문서에 근거가 없으면 지어내지 말고 "정확한 안내가 어려워 상담원 연결이 필요합니다." 라고만 답하세요.
            서비스 고객센터와 무관한 질문에는 답하지 말고 같은 폴백 문구로 안내하세요.
            '참고 문서'라는 표현은 답변에 쓰지 말고 자연스럽게 안내하세요.

            [참고 문서]
            %s
            """;

    private final ChatClient chatClient;
    private final CsVectorStorePort vectorStore;

    public RagCsAnswerAdapter(ChatClient.Builder chatClientBuilder, CsVectorStorePort vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public String generateAnswer(String question) {
        try {
            List<String> contexts = vectorStore.searchSimilar(question, TOP_K);
            String context = contexts.isEmpty() ? "(관련 문서 없음)" : String.join("\n---\n", contexts);

            return chatClient.prompt()
                    .system(SYSTEM_PROMPT.formatted(context))
                    .user(question)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("cs RAG 답변 생성 실패", e);
            throw new CustomException(CsErrorCode.CS_ANSWER_GENERATION_FAILED);
        }
    }
}

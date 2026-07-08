package com.fandom.cs_service.infra.ai;

import com.fandom.cs_service.application.dto.AnswerResult;
import com.fandom.cs_service.application.port.CsAnswerPort;
import com.fandom.cs_service.application.port.CsVectorStorePort;
import com.fandom.cs_service.domain.entity.CsMessage;
import com.fandom.cs_service.domain.entity.SenderRole;
import com.fandom.cs_service.domain.exception.CsErrorCode;
import com.fandom.common.exception.CustomException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "cs.rag.enabled", havingValue = "true")
public class RagCsAnswerAdapter implements CsAnswerPort {

    private static final int TOP_K = 4;

    // 근거 없거나 무관한 질문에 반환하는 폴백 문구(프롬프트와 동일). 폴백 응답률 집계용
    private static final String FALLBACK_ANSWER = "정확한 안내가 어려워 상담원 연결이 필요합니다.";

    private static final String SYSTEM_PROMPT = """
            당신은 크리에이터와 팬이 피드·실시간 채팅으로 소통하고 콘서트 티켓을 선착순 예매하는 팬덤 SNS 서비스의 고객센터 AI 상담원입니다.
            이전 대화 맥락을 참고해 현재 질문의 의도를 파악하되, 답변의 사실 근거는 아래 제공되는 자료에서만 찾아 한국어로 정확하고 간결하게 답변하세요.
            자료에 근거가 없거나 서비스 고객센터와 무관한 질문이면, 지어내지 말고 "정확한 안내가 어려워 상담원 연결이 필요합니다." 라고만 답하세요.
            자료나 문서의 존재를 언급하지 말고, 사용자에게 직접 안내하듯 자연스럽게 답하세요.

            ---
            %s
            """;

    private final ChatClient chatClient;
    private final CsVectorStorePort vectorStore;
    private final MeterRegistry meterRegistry;

    public RagCsAnswerAdapter(ChatClient.Builder chatClientBuilder, CsVectorStorePort vectorStore,
                              MeterRegistry meterRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String generateAnswer(String question, List<CsMessage> history) {
        return generateAnswerDetailed(question, history).answer();
    }

    @Override
    public AnswerResult generateAnswerDetailed(String question, List<CsMessage> history) {
        try {
            List<String> contexts = vectorStore.searchSimilar(question, TOP_K);
            String context = contexts.isEmpty() ? "(관련 문서 없음)" : String.join("\n---\n", contexts);

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SYSTEM_PROMPT.formatted(context)));
            history.forEach(m -> messages.add(m.getSenderRole() == SenderRole.USER
                    ? new UserMessage(m.getContent())
                    : new AssistantMessage(m.getContent())));
            messages.add(new UserMessage(question));

            log.info("cs RAG 멀티턴 이력 {}건 포함", history.size());

            String answer = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();

            // 폴백(근거 없음/무관) 응답 집계 → 무근거·폴백 응답률 감지
            if (answer != null && answer.contains(FALLBACK_ANSWER)) {
                meterRegistry.counter("cs.answer.fallback").increment();
            }

            return new AnswerResult(answer, contexts);
        } catch (Exception e) {
            log.error("cs RAG 답변 생성 실패", e);
            throw new CustomException(CsErrorCode.CS_ANSWER_GENERATION_FAILED);
        }
    }
}

package com.fandom.cs_service.infra.ai;

import com.fandom.cs_service.application.dto.AnswerResult;
import com.fandom.cs_service.application.port.CsAnswerPort;
import com.fandom.cs_service.domain.entity.CsMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "cs.rag.enabled", havingValue = "false", matchIfMissing = true)
public class StubCsAnswerAdapter implements CsAnswerPort {

    private static final String STUB_ANSWER = "문의가 접수되었습니다.";

    @Override
    public String generateAnswer(String question, List<CsMessage> history) {
        log.debug("cs 답변 스텁 응답 반환");
        return STUB_ANSWER;
    }

    @Override
    public AnswerResult generateAnswerDetailed(String question, List<CsMessage> history) {
        return new AnswerResult(STUB_ANSWER, List.of());
    }
}

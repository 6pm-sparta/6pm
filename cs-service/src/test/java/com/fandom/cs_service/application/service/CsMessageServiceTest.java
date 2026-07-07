package com.fandom.cs_service.application.service;

import com.fandom.cs_service.application.port.CsAnswerPort;
import com.fandom.cs_service.domain.entity.CsMessage;
import com.fandom.cs_service.domain.entity.SenderRole;
import com.fandom.cs_service.domain.repository.CsMessageRepository;
import com.fandom.cs_service.presentation.dto.response.CsMessageListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsMessageService 단위 테스트")
class CsMessageServiceTest {

    @Mock
    private CsMessageRepository messageRepository;
    @Mock
    private CsAnswerPort answerPort;

    @InjectMocks
    private CsMessageService service;

    private static final UUID USER = UUID.randomUUID();

    private CsMessage message(SenderRole role, String content) {
        CsMessage m = CsMessage.builder().userId(USER).senderRole(role).content(content).build();
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        return m;
    }

    // ---------- inquire ----------

    @Test
    @DisplayName("문의: 이력 로드 → USER 저장 → 답변 생성 → AI 저장 순서로 처리하고 답변을 반환한다")
    void inquire_savesInOrderAndReturnsAnswer() {
        given(messageRepository.findMessages(eq(USER), isNull(), eq(6))).willReturn(List.of());
        given(answerPort.generateAnswer(eq("환불 되나요?"), anyList())).willReturn("전액 환불됩니다.");

        String answer = service.inquire(USER, "환불 되나요?");

        assertThat(answer).isEqualTo("전액 환불됩니다.");

        ArgumentCaptor<CsMessage> saved = ArgumentCaptor.forClass(CsMessage.class);
        InOrder order = inOrder(messageRepository, answerPort);
        order.verify(messageRepository).findMessages(USER, null, 6);
        order.verify(messageRepository).save(saved.capture());          // USER
        order.verify(answerPort).generateAnswer(eq("환불 되나요?"), anyList());
        order.verify(messageRepository).save(saved.capture());          // AI

        List<CsMessage> both = saved.getAllValues();
        assertThat(both.get(0).getSenderRole()).isEqualTo(SenderRole.USER);
        assertThat(both.get(0).getContent()).isEqualTo("환불 되나요?");
        assertThat(both.get(1).getSenderRole()).isEqualTo(SenderRole.AI);
        assertThat(both.get(1).getContent()).isEqualTo("전액 환불됩니다.");
    }

    @Test
    @DisplayName("문의: 최근 이력을 시간순(오래된 것부터)으로 뒤집어 답변 포트에 전달한다")
    void inquire_passesReversedHistory() {
        CsMessage newer = message(SenderRole.AI, "이전 답변");
        CsMessage older = message(SenderRole.USER, "이전 질문");

        given(messageRepository.findMessages(eq(USER), isNull(), eq(6)))
                .willReturn(List.of(newer, older));
        given(answerPort.generateAnswer(eq("후속 질문"), anyList())).willReturn("답변");

        service.inquire(USER, "후속 질문");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CsMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(answerPort).generateAnswer(eq("후속 질문"), historyCaptor.capture());
        assertThat(historyCaptor.getValue()).containsExactly(older, newer);
    }

    // ---------- getHistory ----------

    @Test
    @DisplayName("이력 조회: limit+1개가 오면 hasNext=true, 마지막 페이지 항목 id가 nextCursor가 된다")
    void getHistory_hasNext() {
        CsMessage m1 = message(SenderRole.USER, "1");
        CsMessage m2 = message(SenderRole.AI, "2");
        CsMessage m3 = message(SenderRole.USER, "3"); // 초과분
        given(messageRepository.findMessages(USER, null, 3)).willReturn(List.of(m1, m2, m3));

        CsMessageListResponse res = service.getHistory(USER, null, 2);

        assertThat(res.hasNext()).isTrue();
        assertThat(res.messages()).hasSize(2);
        assertThat(res.nextCursor()).isEqualTo(m2.getId());
    }

    @Test
    @DisplayName("이력 조회: limit 이하면 hasNext=false")
    void getHistory_noNext() {
        CsMessage m1 = message(SenderRole.USER, "1");
        given(messageRepository.findMessages(USER, null, 3)).willReturn(List.of(m1));

        CsMessageListResponse res = service.getHistory(USER, null, 2);

        assertThat(res.hasNext()).isFalse();
        assertThat(res.messages()).hasSize(1);
        assertThat(res.nextCursor()).isEqualTo(m1.getId());
    }

    @Test
    @DisplayName("이력 조회: 비어있으면 nextCursor는 null, hasNext=false")
    void getHistory_empty() {
        given(messageRepository.findMessages(USER, null, 3)).willReturn(List.of());

        CsMessageListResponse res = service.getHistory(USER, null, 2);

        assertThat(res.messages()).isEmpty();
        assertThat(res.nextCursor()).isNull();
        assertThat(res.hasNext()).isFalse();
    }

    @Test
    @DisplayName("이력 조회: size 미지정이면 기본 20으로 조회한다(limit+1=21)")
    void getHistory_defaultSize() {
        given(messageRepository.findMessages(USER, null, 21)).willReturn(List.of());

        service.getHistory(USER, null, null);

        verify(messageRepository).findMessages(USER, null, 21);
    }

    @Test
    @DisplayName("이력 조회: size가 최대치를 넘으면 100으로 제한한다(limit+1=101)")
    void getHistory_maxClamp() {
        given(messageRepository.findMessages(USER, null, 101)).willReturn(List.of());

        service.getHistory(USER, null, 500);

        verify(messageRepository).findMessages(USER, null, 101);
    }

    // ---------- clearHistory ----------

    @Test
    @DisplayName("문의 초기화: 해당 유저의 메시지를 일괄 소프트 삭제한다")
    void clearHistory_softDeletesAll() {
        service.clearHistory(USER);

        verify(messageRepository).softDeleteAllByUserId(USER);
    }
}

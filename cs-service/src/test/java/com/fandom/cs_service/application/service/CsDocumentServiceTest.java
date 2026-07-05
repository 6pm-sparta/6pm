package com.fandom.cs_service.application.service;

import com.fandom.cs_service.domain.entity.CsDocument;
import com.fandom.cs_service.domain.exception.CsErrorCode;
import com.fandom.cs_service.domain.repository.CsDocumentRepository;
import com.fandom.cs_service.application.port.CsVectorStorePort;
import com.fandom.common.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsDocumentService 단위 테스트")
class CsDocumentServiceTest {

    @Mock
    private CsDocumentRepository documentRepository;
    @Mock
    private CsVectorStorePort vectorStore;

    @InjectMocks
    private CsDocumentService service;

    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID DELETER = UUID.randomUUID();

    private CsDocument doc(String title, String content) {
        CsDocument d = CsDocument.builder().title(title).content(content).build();
        ReflectionTestUtils.setField(d, "id", DOC_ID);
        return d;
    }

    // ---------- create ----------

    @Test
    @DisplayName("등록: 원문 저장 후 같은 id로 벡터를 적재한다")
    void create_savesDocAndVector() {
        given(documentRepository.save(any(CsDocument.class))).willReturn(doc("환불", "전액 환불"));

        CsDocument result = service.create("환불", "전액 환불");

        assertThat(result.getId()).isEqualTo(DOC_ID);
        verify(vectorStore).save(DOC_ID, "환불", "전액 환불");
    }

    // ---------- get ----------

    @Test
    @DisplayName("상세: 존재하면 문서를 반환한다")
    void get_found() {
        CsDocument d = doc("환불", "전액 환불");
        given(documentRepository.findById(DOC_ID)).willReturn(Optional.of(d));

        assertThat(service.get(DOC_ID)).isSameAs(d);
    }

    @Test
    @DisplayName("상세: 없으면 CS_DOCUMENT_NOT_FOUND 예외")
    void get_notFound() {
        given(documentRepository.findById(DOC_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(DOC_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CsErrorCode.CS_DOCUMENT_NOT_FOUND);
    }

    // ---------- update ----------

    @Test
    @DisplayName("수정: 원문 내용을 교체하고 같은 id로 벡터를 재적재한다")
    void update_replacesContentAndVector() {
        CsDocument d = doc("옛제목", "옛내용");
        given(documentRepository.findById(DOC_ID)).willReturn(Optional.of(d));
        given(documentRepository.save(any(CsDocument.class))).willReturn(d);

        service.update(DOC_ID, "새제목", "새내용");

        assertThat(d.getTitle()).isEqualTo("새제목");
        assertThat(d.getContent()).isEqualTo("새내용");
        verify(vectorStore).save(DOC_ID, "새제목", "새내용");
    }

    @Test
    @DisplayName("수정: 없으면 예외이고 벡터를 건드리지 않는다")
    void update_notFound() {
        given(documentRepository.findById(DOC_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(DOC_ID, "t", "c"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CsErrorCode.CS_DOCUMENT_NOT_FOUND);
        verify(vectorStore, never()).save(any(), any(), any());
    }

    // ---------- delete ----------

    @Test
    @DisplayName("삭제: 원문을 소프트 삭제하고 벡터를 제거한다")
    void delete_softDeletesAndRemovesVector() {
        CsDocument d = doc("환불", "전액 환불");
        given(documentRepository.findById(DOC_ID)).willReturn(Optional.of(d));

        service.delete(DOC_ID, DELETER);

        assertThat(d.isDeleted()).isTrue();
        verify(documentRepository).save(d);
        verify(vectorStore).delete(DOC_ID);
    }

    @Test
    @DisplayName("삭제: 없으면 예외이고 벡터를 건드리지 않는다")
    void delete_notFound() {
        given(documentRepository.findById(DOC_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(DOC_ID, DELETER))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(CsErrorCode.CS_DOCUMENT_NOT_FOUND);
        verify(vectorStore, never()).delete(any());
    }

    // ---------- list ----------

    @Test
    @DisplayName("목록: 리포지토리 페이지를 그대로 반환한다")
    void list_delegates() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<CsDocument> page = new PageImpl<>(List.of(doc("환불", "전액 환불")), pageable, 1);
        given(documentRepository.findAll(pageable)).willReturn(page);

        assertThat(service.list(pageable)).isSameAs(page);
    }
}

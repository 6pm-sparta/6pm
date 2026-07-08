# CS Service 아키텍처

## 도메인 모델

### CsMessage

`CsMessage`는 문의 또는 답변 한 건이다.

주요 속성:

- id
- userId
- senderRole (USER / AI)
- content

`(user_id, id)` 인덱스로 사용자별 최신순 커서 조회를 지원한다. soft delete 대상이다. 하나의 문의는 USER 메시지 1건 + AI 메시지 1건으로 저장된다.

### CsDocument

`CsDocument`는 지식베이스 정책문서(원문)다.

주요 속성:

- id
- title
- content

`cs_documents`는 원문의 **source of truth**이며 soft delete 대상이다. 임베딩 벡터는 이 엔티티가 아니라 Spring AI가 관리하는 `vector_store` 테이블에 별도로 저장되고, 두 테이블은 문서 id로 연결된다.

### SenderRole

메시지 작성자 구분값이다.

| 값 | 의미 |
| --- | --- |
| USER | 문의한 사용자 |
| AI | 챗봇 답변 |

## Repository 계층 구조

domain 포트와 infrastructure 구현체로 분리한다.

```text
domain/repository
  CsMessageRepository
  CsDocumentRepository

infra/repository
  CsMessageJpaRepository / CsMessageRepositoryImpl
  CsDocumentJpaRepository / CsDocumentRepositoryImpl
```

커서 조회(`findMessages`), 일괄 soft delete(`softDeleteAllByUserId`), 오프셋 목록(`findAll(pageable)`) 등은 infrastructure 계층에서 담당한다.

## 삭제 정책

| 엔티티 | 삭제 정책 | 이유 |
| --- | --- | --- |
| CsMessage | soft delete | 문의 이력 보존 |
| CsDocument | soft delete | 문서 이력 보존 |
| vector_store | hard delete(문서 삭제/갱신 시) | Spring AI 관리 검색 사본, 원문과 동기화 |

문서 삭제 시 원문은 soft delete, 벡터는 제거해 검색에서 즉시 빠진다.

## 예외 및 응답 정책

공통 응답 형식 `ApiResponse`와 `CustomException`/`ErrorCode`를 사용한다.

주요 에러 코드:

- `CS_ACCESS_DENIED` (403) — MASTER 아님
- `CS_DOCUMENT_NOT_FOUND` (404)
- `CS_ANSWER_GENERATION_FAILED` (500) — RAG 답변 생성 실패

RAG 관련 빈(문서 관리 API/서비스, 벡터·RAG 어댑터)은 `cs.rag.enabled=true`에서만 등록된다.

## 설정 레퍼런스

| 프로퍼티 | 기본/값 | 설명 |
| --- | --- | --- |
| `cs.rag.enabled` | (미설정=false) | RAG·문서관리·평가 빈 활성화. false/미설정이면 스텁 |
| `spring.ai.model.chat` | `ollama` | 챗 프로바이더(`ollama`/`openai`). `CS_CHAT_PROVIDER`로 전환 |
| `spring.ai.model.embedding` | `ollama` | 임베딩 프로바이더(nomic-embed-text, 768차원 고정) |

아래는 외부 `6pm-config`에서 주입한다(레포에는 없음): `spring.ai.ollama.*`(base-url, chat/embedding 모델), `spring.ai.openai.*`(Gemini OpenAI-호환 base-url·api-key·모델), `spring.ai.vectorstore.pgvector.*`(`initialize-schema` 등).

코드 상수: `TOP_K=4`(가져오는 유사 문서 개수, 임계값 없음), `HISTORY_MESSAGES=6`(최근 3턴).

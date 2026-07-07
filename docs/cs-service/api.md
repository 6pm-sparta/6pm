# CS Service API

## 문의/답변 흐름

```text
Client
  -> CsController (POST /api/v1/cs/inquiries)
  -> CsMessageService.inquire
       1) 최근 이력 로드(3턴)
       2) USER 메시지 저장
       3) CsAnswerPort.generateAnswer  (LLM/임베딩, 트랜잭션 밖)
       4) AI 메시지 저장
  -> 답변 반환
```

처리 규칙:

- `inquire`는 `@Transactional`을 걸지 않는다. LLM/임베딩 같은 외부 I/O를 트랜잭션 안에 두지 않기 위해서다. 각 저장은 개별 트랜잭션으로 커밋된다.
- 현재 질문을 저장하기 **전에** 최근 이력을 조회해, 방금 질문이 맥락에 섞이지 않게 한다.
- 사용자 문의는 `CsAnswerPort.generateAnswer`로 `answer`(문자열)만 받는다. 별도로 `generateAnswerDetailed`가 있어 답변과 검색 컨텍스트를 함께(`AnswerResult(answer, contexts)`) 반환하는데, 이는 이력에 저장하지 않는 RAGAS 평가 전용 경로다.

대표 API:

- `POST /api/v1/cs/inquiries` — 문의(답변 반환)
- `GET /api/v1/cs/inquiries` — 이력 조회(커서 페이징, 기본 20 / 최대 100)
- `DELETE /api/v1/cs/inquiries` — 이력 초기화(soft delete)

## 정책문서 관리 (MASTER 전용)

`cs.rag.enabled=true`일 때만 노출되며, 요청자의 IdCard role이 MASTER가 아니면 `CS_ACCESS_DENIED`로 거부한다.

| 기능 | API | 설명 |
| --- | --- | --- |
| 등록 | `POST /api/v1/cs/documents` | 원문 저장 후 같은 id로 벡터 적재 |
| 목록 | `GET /api/v1/cs/documents` | 오프셋 페이징(관리 콘솔용, createdAt DESC) |
| 상세 | `GET /api/v1/cs/documents/{documentId}` | content 포함 상세 |
| 수정 | `PUT /api/v1/cs/documents/{documentId}` | 원문 갱신 + 같은 id로 벡터 재적재 |
| 삭제 | `DELETE /api/v1/cs/documents/{documentId}` | 원문 soft delete + 벡터 제거 |

목록은 데스크톱 관리 콘솔 성격이라 커서가 아닌 오프셋 페이징을 쓴다. 없는 문서 접근은 `CS_DOCUMENT_NOT_FOUND`.

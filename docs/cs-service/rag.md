# RAG 파이프라인

## RAG 파이프라인

답변 생성은 `CsAnswerPort` 뒤에서 이뤄지며, 설정(`cs.rag.enabled`)에 따라 두 구현이 교체된다.

| 구현 | 조건 | 동작 |
| --- | --- | --- |
| StubCsAnswerAdapter | `cs.rag.enabled=false` | 고정 응답 반환(인프라 없이 흐름 테스트용) |
| RagCsAnswerAdapter | `cs.rag.enabled=true` | 실제 RAG 답변 생성 |

RAG 답변 흐름:

1. 질문을 임베딩해 `vector_store`에서 유사 문서 top-K 검색 (`TOP_K=4`, **유사도 임계값 없음** — 점수와 무관하게 상위 4건을 그대로 사용)
2. 검색 결과 본문을 `\n---\n`로 이어 컨텍스트로 구성(결과가 없으면 `(관련 문서 없음)`)
3. `SystemMessage`(프롬프트+컨텍스트) → 이전 대화 이력(User/Assistant) → 현재 질문(`UserMessage`) 순으로 메시지 리스트를 만들어 LLM에 전달
4. LLM 답변 반환

임계값이 없어 무관한 문서도 컨텍스트에 섞일 수 있고, 이것이 아래 품질 평가 엔드포인트(RAGAS)의 낮은 context_precision과 직접 연결된다. 근거 없는 답변 차단은 프롬프트의 폴백 지시에 의존한다.

시스템 프롬프트 원문(`%s`에 컨텍스트가 주입됨):

```text
당신은 크리에이터와 팬이 피드·실시간 채팅으로 소통하고 콘서트 티켓을 선착순 예매하는 팬덤 SNS 서비스의 고객센터 AI 상담원입니다.
이전 대화 맥락을 참고해 현재 질문의 의도를 파악하되, 답변의 사실 근거는 아래 제공되는 자료에서만 찾아 한국어로 정확하고 간결하게 답변하세요.
자료에 근거가 없거나 서비스 고객센터와 무관한 질문이면, 지어내지 말고 "정확한 안내가 어려워 상담원 연결이 필요합니다." 라고만 답하세요.
자료나 문서의 존재를 언급하지 말고, 사용자에게 직접 안내하듯 자연스럽게 답하세요.

---
%s
```

### 품질 평가 엔드포인트 (RAGAS)

RAG 파이프라인 품질을 정량 평가하기 위한 전용 엔드포인트가 있다.

| 항목 | 내용 |
| --- | --- |
| API | `POST /api/v1/cs/eval` |
| 노출 조건 | `cs.rag.enabled=true` (`CsEvalController`) |
| 권한 | MASTER 전용(IdCard role 검증) |
| 반환 | `answer` + `contexts`(검색된 컨텍스트 목록) |

일반 문의와 달리 이력을 저장하지 않고, 이력 없이(`generateAnswerDetailed(question, [])`) 답변과 검색 컨텍스트를 함께 돌려준다. 오프라인 하니스(`cs-service/eval/ragas`)가 이 엔드포인트로 데이터셋을 던져 faithfulness/answer_relevancy/context_precision/context_recall을 측정한다. 심판은 Gemini로 고정하고 답변 생성기(Ollama/Gemini)만 바꿔 비교한다. 상세는 해당 디렉터리 README 참고.

## 멀티턴 대화

`CsMessageService`가 `cs_messages`에서 해당 사용자 최근 3턴(질문+답변 6개)을 시간순으로 불러와 `CsAnswerPort`에 전달한다. `RagCsAnswerAdapter`는 이를 대화 메시지(User/Assistant)로 변환해 `system → 이력 → 현재 질문` 순서로 LLM에 넣는다.

이력을 통해 "그건 언제까지야?" 같은 지시어 후속 질문의 의도를 해석하되, 사실 답변의 근거는 검색된 문서에서만 찾는다. 문의 초기화(`clearHistory`) 시 이력이 비워져 새 대화로 시작된다.

## 원문/벡터 동기화

`CsVectorStorePort`가 원문 변경을 벡터스토어에 반영한다.

| 포트 메서드 | 동작 |
| --- | --- |
| `save(documentId, title, content)` | 문서 등록/갱신. 문서 id를 벡터 id로 써서 기존 벡터 제거 후 재적재 |
| `delete(documentId)` | 벡터 제거 |
| `searchSimilar(query, topK)` | 질문 유사 문서 본문 반환 |

`PgVectorStoreAdapter`는 Spring AI `VectorStore`(pgvector) 위에서 동작한다. `vector_store` 테이블은 `initialize-schema=true`로 Spring AI가 생성하며, 컬럼은 id/content/metadata/embedding(vector(768))이다. metadata에 `csDocumentId`, `title`을 담는다.

`cs_documents`(원문·관리) 와 `vector_store`(검색용 임베딩)는 역할이 분리되어 있고 문서 id로 묶인다.

## LLM 프로바이더 전환

임베딩·챗 모델은 Spring AI 프로바이더 셀렉터로 선택한다.

| 설정 | 기본 | 대안 |
| --- | --- | --- |
| `spring.ai.model.chat` | `ollama` (qwen2.5:3b) | `openai` (Gemini, OpenAI 호환 엔드포인트) |
| `spring.ai.model.embedding` | `ollama` (nomic-embed-text, 768) | 고정 |

- 챗 프로바이더는 `CS_CHAT_PROVIDER` 환경변수로 재빌드 없이 전환한다. `openai`로 두면 Gemini(AI Studio API 키, `GEMINI_API_KEY`)를 사용한다.
- 임베딩은 nomic 고정이다. 임베딩 프로바이더를 바꾸면 벡터 공간이 달라져 전 문서를 재임베딩해야 하므로 고정으로 둔다.
- `CsAnswerPort`/`RagCsAnswerAdapter`는 프로바이더에 독립적이라(추상화 사용) 전환 시 코드 변경이 없다.

pgvector 차원(768)은 임베딩 모델과 일치해야 한다.

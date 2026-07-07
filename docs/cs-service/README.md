# CS Service 설계

CS(고객센터) Service의 책임, 도메인 모델, 문의/답변 흐름, RAG 파이프라인, 정책문서 관리, 이벤트 연동을 관심사별 문서로 나눈 설계 문서 모음이다.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [architecture.md](architecture.md) | 도메인 모델, Repository 계층 구조, 삭제 정책, 예외 및 응답 정책, 설정 레퍼런스 |
| [rag.md](rag.md) | RAG 파이프라인, 품질 평가 엔드포인트, 멀티턴 대화, 원문/벡터 동기화, LLM 프로바이더 전환 |
| [api.md](api.md) | 문의/답변 흐름과 API, 정책문서 관리(MASTER 전용) API |
| [events.md](events.md) | 이벤트 연동, Kafka 운영 계약 |
| [cs-knowledge-base.md](cs-knowledge-base.md) | 지식베이스(정책문서) 자료 |

## 빠른 참조

- **엔드포인트**: 문의 `POST/GET/DELETE /api/v1/cs/inquiries` / 문서 관리(MASTER) `POST/GET/PUT/DELETE /api/v1/cs/documents` / 품질 평가(MASTER) `POST /api/v1/cs/eval`
- **Kafka**: 소비 `user.deleted` (발행 없음)
- **설정**: `cs.rag.enabled`, `CS_CHAT_PROVIDER`(`ollama`/`openai`), 상수 `TOP_K=4`·`HISTORY_MESSAGES=6`(최근 3턴)

## 문서 목적

이 문서는 CS(고객센터) Service의 책임, 도메인 모델, 문의/답변 흐름, RAG 파이프라인, 정책문서 관리(MASTER), LLM 프로바이더 전환, 멀티턴 대화, 이벤트 연동, 삭제 정책, repository 계층 구조를 정리한다.

## CS Service 책임 범위

CS Service는 AI 기반 고객센터 챗봇 도메인을 담당한다.

주요 책임은 다음과 같다.

- 사용자 문의 접수 및 AI 답변 생성(RAG)
- 문의/답변 이력 저장·조회·초기화
- 멀티턴 대화(이전 문답 맥락 유지)
- 정책문서(지식베이스) 관리 — MASTER 전용
- 원문(cs_documents)과 벡터(vector_store) 동기화
- LLM 프로바이더 전환(로컬 Ollama ↔ Gemini)
- 회원 탈퇴 시 문의 이력 정리

답변 근거가 되는 정책문서는 MASTER가 등록하고, 사용자 문의는 이 문서들을 검색해 LLM이 답변한다.
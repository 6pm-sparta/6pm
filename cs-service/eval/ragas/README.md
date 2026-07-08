# CS RAG 평가 (RAGAS)

CS 챗봇 RAG 파이프라인 품질을 [RAGAS](https://docs.ragas.io/)로 정량 평가하는 오프라인 하니스다. Spring 빌드와 무관한 파이썬 도구다.

**심판(judge)은 Gemini로 고정**하고, 답변 생성기(Ollama/Gemini)만 바꿔가며 비교한다. (자기 평가 편향을 피하려고 채점자는 항상 상위 모델 하나로 통일)

## 측정 지표

| 지표 | 의미 | 필요 입력 |
| --- | --- | --- |
| faithfulness | 답변이 검색 컨텍스트에 충실한가(환각 여부) | answer, contexts |
| answer_relevancy | 답변이 질문에 관련 있나 | question, answer |
| context_precision | 검색된 컨텍스트가 관련 있고 상위에 있나(검색 순위) | question, contexts, ground_truth |
| context_recall | 정답을 뒷받침할 컨텍스트가 검색됐나 | contexts, ground_truth |

앞 2개는 생성 품질, 뒤 2개는 검색 품질을 본다.

## 사전 조건

- CS 서비스가 `cs.rag.enabled=true`로 떠 있어야 한다(평가 엔드포인트 `POST /api/v1/cs/eval`는 RAG 활성화 시에만 노출).
- 지식베이스 문서가 등록돼 있어야 한다(`docs/cs-service/cs-documents-seed.json` 참고).
- 평가 엔드포인트는 MASTER 전용이라 MASTER IdCard 서명이 필요하다(스크립트가 자동 생성).

## 설치

```bash
cd cs-service/eval/ragas
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt   # Windows: activate 없이 직접 호출
```

## 실행

```powershell
# 1) Ollama 답변 → Gemini 채점  (CS 서비스: CS_CHAT_PROVIDER=ollama)
$env:CS_ANSWER_LABEL="ollama"
$env:GEMINI_API_KEY="<AI Studio 키>"
$env:CS_MASTER_USER_ID="<마스터 UUID>"
$env:HMAC_SECRET="<config의 IdCard 시크릿>"
.\.venv\Scripts\python.exe run_ragas.py      # → results_ollama_by_gemini.csv

# 2) Gemini 답변 → Gemini 채점  (CS 서비스: CS_CHAT_PROVIDER=openai 로 재시작)
$env:CS_ANSWER_LABEL="gemini"
.\.venv\Scripts\python.exe run_ragas.py      # → results_gemini_by_gemini.csv
```

두 CSV(`results_ollama_by_gemini.csv` vs `results_gemini_by_gemini.csv`)를 비교하면 답변 생성기 성능 차이를 볼 수 있다.

## 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `GEMINI_API_KEY` | (필수) | 심판(Gemini) AI Studio 키 |
| `CS_MASTER_USER_ID` | (필수) | MASTER 사용자 UUID |
| `CS_ANSWER_LABEL` | `unknown` | 답변 생성기 라벨(파일명 구분): ollama \| gemini |
| `CS_EVAL_URL` | `http://localhost:8089/api/v1/cs/eval` | 평가 엔드포인트 |
| `HMAC_SECRET` | (필수) | IdCard 서명 키(config의 값과 동일하게 지정, 기본값 없음) |
| `RAGAS_TIMEOUT` | `600` | 지표당 타임아웃(초) |
| `RAGAS_WORKERS` | `1` | 동시 실행 수(429 나면 그대로, 빠르게 하려면 상향) |

## 데이터셋

`dataset.json` — 질문 + ground_truth(기준 답변). 지식베이스(`cs-knowledge-base.md`) 내용에 맞춰 작성돼 있다. 문서를 바꾸면 이 파일도 함께 갱신한다.
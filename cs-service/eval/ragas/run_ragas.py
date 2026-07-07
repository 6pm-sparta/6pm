"""
CS RAG RAGAS 평가 (심판=Gemini 고정)

동작:
  1) dataset.json(질문 + ground_truth) 로드
  2) 각 질문을 CS 평가 엔드포인트(POST /api/v1/cs/eval)에 던져 answer + contexts 수집
  3) RAGAS 4종 지표로 평가: faithfulness, answer_relevancy, context_precision, context_recall
  4) 콘솔 출력 + results_{답변}_by_gemini.csv 저장

심판(judge)은 Gemini로 고정. 답변 생성기(ollama/gemini)만 바꿔가며 비교한다.

필수 환경변수:
  GEMINI_API_KEY       AI Studio 키 (심판용)
  CS_MASTER_USER_ID    MASTER 사용자 UUID (평가 엔드포인트 인증용)
  HMAC_SECRET          IdCard 서명 시크릿 (config의 값과 동일하게 주입)
선택 환경변수:
  CS_ANSWER_LABEL      답변 생성기 라벨(파일명 구분): ollama | gemini (기본 unknown)
  CS_EVAL_URL          평가 엔드포인트 URL (기본 http://localhost:8089/api/v1/cs/eval)
  RAGAS_TIMEOUT        지표당 타임아웃 초 (기본 600)
  RAGAS_WORKERS        동시 실행 수 (기본 1; 429 나면 그대로, 빠르게 하려면 상향)

사용:
  pip install -r requirements.txt
  CS_ANSWER_LABEL=ollama GEMINI_API_KEY=... CS_MASTER_USER_ID=... python run_ragas.py
"""

import base64
import hashlib
import hmac
import json
import os
import sys
from pathlib import Path

import requests

# ---- 설정 ----
ANSWER_LABEL = os.getenv("CS_ANSWER_LABEL", "unknown")  # 답변 생성기 라벨(파일명용)
CS_EVAL_URL = os.getenv("CS_EVAL_URL", "http://localhost:8089/api/v1/cs/eval")
CS_MASTER_USER_ID = os.getenv("CS_MASTER_USER_ID", "")
HMAC_SECRET = os.getenv("HMAC_SECRET")  # 기본값 없음: 반드시 환경변수로 주입

DATASET_PATH = Path(__file__).parent / "dataset.json"


def build_id_card_headers() -> dict:
    if not CS_MASTER_USER_ID:
        sys.exit("CS_MASTER_USER_ID 환경변수가 필요합니다 (MASTER 사용자 UUID).")
    if not HMAC_SECRET:
        sys.exit("HMAC_SECRET 환경변수가 필요합니다 (IdCard 서명 키).")
    id_card = '{"userId":"%s","role":"MASTER"}' % CS_MASTER_USER_ID
    signature = base64.b64encode(
        hmac.new(HMAC_SECRET.encode(), id_card.encode(), hashlib.sha256).digest()
    ).decode()
    return {
        "Content-Type": "application/json",
        "X-Id-Card": id_card,
        "X-Id-Card-Signature": signature,
    }


def collect_samples(headers: dict) -> list[dict]:
    dataset = json.loads(DATASET_PATH.read_text(encoding="utf-8"))
    samples = []
    for i, row in enumerate(dataset, 1):
        question = row["question"]
        resp = requests.post(
            CS_EVAL_URL, headers=headers,
            data=json.dumps({"question": question}, ensure_ascii=False).encode("utf-8"),
            timeout=120,
        )
        resp.raise_for_status()
        data = resp.json().get("data", resp.json())
        answer = data.get("answer", "")
        contexts = data.get("contexts", []) or []
        print(f"[{i}/{len(dataset)}] {question} -> contexts={len(contexts)}")
        samples.append({
            "user_input": question,
            "retrieved_contexts": contexts,
            "response": answer,
            "reference": row["ground_truth"],
        })
    return samples


def build_judge():
    from ragas.llms import LangchainLLMWrapper
    from ragas.embeddings import LangchainEmbeddingsWrapper
    from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings

    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        sys.exit("GEMINI_API_KEY 환경변수가 필요합니다.")
    judge_model = os.getenv("RAGAS_JUDGE_MODEL", "gemini-3.1-flash-lite")
    llm = ChatGoogleGenerativeAI(model=judge_model, google_api_key=api_key)
    emb = GoogleGenerativeAIEmbeddings(model="models/gemini-embedding-001", google_api_key=api_key)
    return LangchainLLMWrapper(llm), LangchainEmbeddingsWrapper(emb)


def main():
    headers = build_id_card_headers()
    print(f"== CS RAGAS 평가 시작 (answer={ANSWER_LABEL}, judge=gemini) ==")

    samples = collect_samples(headers)

    from ragas import EvaluationDataset, evaluate
    from ragas.run_config import RunConfig
    from ragas.metrics import (
        Faithfulness,
        ResponseRelevancy,
        LLMContextPrecisionWithReference,
        LLMContextRecall,
    )

    judge_llm, judge_emb = build_judge()
    eval_dataset = EvaluationDataset.from_list(samples)

    metrics = [
        Faithfulness(),
        ResponseRelevancy(),
        LLMContextPrecisionWithReference(),
        LLMContextRecall(),
    ]

    run_config = RunConfig(
        timeout=int(os.getenv("RAGAS_TIMEOUT", 600)),
        max_workers=int(os.getenv("RAGAS_WORKERS", 1)),
    )

    result = evaluate(
        dataset=eval_dataset,
        metrics=metrics,
        llm=judge_llm,
        embeddings=judge_emb,
        run_config=run_config,
    )

    print("\n== 결과 ==")
    print(result)

    df = result.to_pandas()
    out = Path(__file__).parent / f"results_{ANSWER_LABEL}_by_gemini.csv"
    df.to_csv(out, index=False, encoding="utf-8-sig")
    print(f"\n상세 결과 저장: {out}")


if __name__ == "__main__":
    main()

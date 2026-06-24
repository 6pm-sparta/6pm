package com.fandom.aiops_service.application.dto;

/**
 * LLM(Gemini) 장애 분석 결과. Spring AI structured output(.entity)로 매핑된다.
 * 필드명이 곧 LLM에게 요구하는 JSON 스키마가 되므로 의미가 분명해야 한다.
 *
 * @param summary   에러 요약 (무슨 일이 일어났는가, 1~2문장)
 * @param rootCause 원인 추정 (가장 가능성 높은 근본 원인 가설)
 * @param guide     개선/복구 가이드 (담당자가 바로 할 수 있는 조치)
 */
public record AiAnalysisResult(
        String summary,
        String rootCause,
        String guide
) {
}

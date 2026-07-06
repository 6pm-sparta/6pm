package com.fandom.cs_service.presentation.dto.response;

import com.fandom.cs_service.application.dto.AnswerResult;

import java.util.List;

// RAGAS 평가 전용
public record EvalResponse(String answer, List<String> contexts) {

    public static EvalResponse from(AnswerResult result) {
        return new EvalResponse(result.answer(), result.contexts());
    }
}

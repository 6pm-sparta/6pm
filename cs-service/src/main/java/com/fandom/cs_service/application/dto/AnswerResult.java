package com.fandom.cs_service.application.dto;

import java.util.List;

// RAGAS 평가 전용
public record AnswerResult(String answer, List<String> contexts) {
}

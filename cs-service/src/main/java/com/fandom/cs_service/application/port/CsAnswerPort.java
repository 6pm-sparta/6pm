package com.fandom.cs_service.application.port;

import com.fandom.cs_service.domain.entity.CsMessage;

import java.util.List;

public interface CsAnswerPort {

    String generateAnswer(String question, List<CsMessage> history);
}

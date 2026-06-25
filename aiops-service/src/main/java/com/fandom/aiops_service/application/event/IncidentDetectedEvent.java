package com.fandom.aiops_service.application.event;

import java.util.UUID;

public record IncidentDetectedEvent(UUID incidentId) {
}

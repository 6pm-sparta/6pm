package com.fandom.notification_service.application.dto;

import java.util.List;

public record DispatchPlan(String title, String body, List<DeliveryTarget> targets) {

    public static DispatchPlan empty() {
        return new DispatchPlan(null, null, List.of());
    }
}

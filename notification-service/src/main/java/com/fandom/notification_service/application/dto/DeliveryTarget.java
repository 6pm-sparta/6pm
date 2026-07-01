package com.fandom.notification_service.application.dto;

import com.fandom.notification_service.domain.entity.DeviceType;

public record DeliveryTarget(String deviceToken, DeviceType deviceType) {
}

package com.fandom.notification_service.support;

// 디바이스 토큰 식별용 앞 4자만 노출
public final class LogMask {

    private LogMask() {
    }

    public static String token(String value) {
        if (value == null || value.isBlank()) {
            return "null";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "****";
    }
}

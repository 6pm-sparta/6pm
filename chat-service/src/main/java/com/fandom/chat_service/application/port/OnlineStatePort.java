package com.fandom.chat_service.application.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OnlineStatePort {

    // ws 연결 여부
    void markOnline(UUID userId);

    void markOffline(UUID userId);

    boolean isOnline(UUID userId);

    // 온라인 유저 리스트
    List<UUID> filterOnline(Collection<UUID> userIds);
}

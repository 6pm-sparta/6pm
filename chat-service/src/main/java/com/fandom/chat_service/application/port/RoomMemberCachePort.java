package com.fandom.chat_service.application.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

// 방 팬 셋 캐시
public interface RoomMemberCachePort {

    boolean exists(UUID roomId);

    Set<UUID> getMembers(UUID roomId);

    // 전체 셋 적재
    void cacheMembers(UUID roomId, Collection<UUID> userIds);

    // 이미 캐시된 경우에만 추가
    void addIfPresent(UUID roomId, UUID userId);

    void remove(UUID roomId, UUID userId);

    void evict(UUID roomId);
}

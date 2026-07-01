package com.fandom.chat_service.application.service;

import com.fandom.chat_service.application.port.RoomMemberCachePort;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomMemberCacheService {

    private final RoomMemberCachePort cache;
    private final ChatRoomMemberRepository memberRepository;

    // 브로드캐스트 대상
    public Set<UUID> getFans(UUID roomId, UUID creatorId) {
        if (cache.exists(roomId)) {
            return cache.getMembers(roomId);
        }
        Set<UUID> fans = memberRepository.findUserIdsByRoomId(roomId).stream()
                .filter(userId -> !userId.equals(creatorId))
                .collect(Collectors.toSet());
        cache.cacheMembers(roomId, fans);
        return fans;
    }

    public void onMemberAdded(UUID roomId, UUID userId) {
        cache.addIfPresent(roomId, userId);
    }

    public void onMemberRemoved(UUID roomId, UUID userId) {
        cache.remove(roomId, userId);
    }

    public void evictRoom(UUID roomId) {
        cache.evict(roomId);
    }
}

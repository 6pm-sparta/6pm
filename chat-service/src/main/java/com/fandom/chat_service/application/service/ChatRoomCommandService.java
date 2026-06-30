package com.fandom.chat_service.application.service;

import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.entity.ChatRoomMember;
import com.fandom.chat_service.domain.exception.ChatErrorCode;
import com.fandom.chat_service.domain.repository.ChatMessageRepository;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import com.fandom.chat_service.domain.repository.ChatRoomRepository;
import com.fandom.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomCommandService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final RoomMemberCacheService roomMemberCache;

    // 크리에이터 생성/전환
    @Transactional
    public void handleCreatorCreated(UUID creatorId, String nickname) {
        if (creatorId == null) {
            log.warn("creator-created user_id 없음 - 스킵");
            return;
        }
        if (roomRepository.findByCreatorId(creatorId).isPresent()) {
            log.info("이미 존재하는 크리에이터 방 - 스킵 creator_id={}", creatorId);
            return;
        }
        ChatRoom room = roomRepository.save(ChatRoom.builder()
                .creatorId(creatorId)
                .title(nickname)
                .build());
        memberRepository.save(ChatRoomMember.builder()
                .roomId(room.getId())
                .userId(creatorId)
                .nickname(nickname)
                .build());
        log.info("채팅방 생성 room_id={}, creator_id={}", room.getId(), creatorId);
    }

    // 팔로우
    @Transactional
    public void handleFollowed(UUID followeeId, UUID followerId, String nickname) {
        if (followeeId == null || followerId == null) {
            log.warn("followed id 누락 - 스킵 followee_id={}, follower_id={}", followeeId, followerId);
            return;
        }
        ChatRoom room = roomRepository.findByCreatorId(followeeId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.ROOM_NOT_FOUND));
        if (memberRepository.existsByRoomIdAndUserId(room.getId(), followerId)) {
            return;
        }
        memberRepository.save(ChatRoomMember.builder()
                .roomId(room.getId())
                .userId(followerId)
                .nickname(nickname)
                .build());
        afterCommit(() -> roomMemberCache.onMemberAdded(room.getId(), followerId));
        log.info("입장 room_id={}, user_id={}", room.getId(), followerId);
    }

    // 언팔
    @Transactional
    public void handleUnfollowed(UUID followeeId, UUID followerId) {
        if (followeeId == null || followerId == null) {
            log.warn("unfollowed id 누락 - 스킵 followee_id={}, follower_id={}", followeeId, followerId);
            return;
        }
        roomRepository.findByCreatorId(followeeId).ifPresentOrElse(
                room -> {
                    memberRepository.deleteByRoomIdAndUserId(room.getId(), followerId);
                    afterCommit(() -> roomMemberCache.onMemberRemoved(room.getId(), followerId));
                    log.info("퇴장 room_id={}, user_id={}", room.getId(), followerId);
                },
                () -> log.warn("퇴장 대상 방 없음 - 스킵 followee_id={}, user_id={}", followeeId, followerId)
        );
    }

    // 탈퇴
    @Transactional
    public void handleUserDeleted(UUID userId) {
        if (userId == null) {
            log.warn("user.deleted user_id 없음 - 스킵");
            return;
        }
        // 삭제 전에 멤버였던 방 목록
        List<UUID> memberRoomIds = memberRepository.findAllByUserId(userId).stream()
                .map(ChatRoomMember::getRoomId)
                .toList();

        roomRepository.findByCreatorId(userId).ifPresent(room -> {
            messageRepository.softDeleteAllByRoomId(room.getId(), userId);
            memberRepository.deleteByRoomId(room.getId());
            room.softDelete(userId);
            roomRepository.save(room);
            afterCommit(() -> roomMemberCache.evictRoom(room.getId()));
            log.info("크리에이터 탈퇴 - 방 삭제 room_id={}, creator_id={}", room.getId(), userId);
        });
        memberRepository.deleteByUserId(userId);
        afterCommit(() -> memberRoomIds.forEach(roomId -> roomMemberCache.onMemberRemoved(roomId, userId)));
        log.info("탈퇴 멤버십 정리 완료 user_id={}", userId);
    }

    // 트랜잭션 커밋 후 캐시 반영
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

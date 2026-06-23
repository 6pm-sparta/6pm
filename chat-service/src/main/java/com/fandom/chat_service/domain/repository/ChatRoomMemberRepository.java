package com.fandom.chat_service.domain.repository;

import com.fandom.chat_service.domain.entity.ChatRoomMember;

import java.util.List;
import java.util.UUID;

public interface ChatRoomMemberRepository {

    ChatRoomMember save(ChatRoomMember member);

    // 멱등 입장 체크
    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    // 내가 속한 방 목록
    List<ChatRoomMember> findAllByUserId(UUID userId);

    // 방 참여자 목록
    List<ChatRoomMember> findAllByRoomId(UUID roomId);

    // 퇴장(언팔) - 하드 삭제
    void deleteByRoomIdAndUserId(UUID roomId, UUID userId);

    // 방 삭제 시 멤버 일괄 정리 - 하드 삭제
    void deleteByRoomId(UUID roomId);

    // 유저 탈퇴 시 멤버십 일괄 정리 - 하드 삭제
    void deleteByUserId(UUID userId);
}

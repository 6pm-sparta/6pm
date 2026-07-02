package com.fandom.chat_service.application.service;

import com.fandom.chat_service.application.port.RoomMemberCachePort;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomMemberCacheService 단위 테스트")
class RoomMemberCacheServiceTest {

    @Mock
    private RoomMemberCachePort cache;
    @Mock
    private ChatRoomMemberRepository memberRepository;

    @InjectMocks
    private RoomMemberCacheService cacheService;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();

    @Test
    @DisplayName("getFans: 캐시 히트면 캐시값을 반환하고 DB를 조회하지 않는다")
    void getFans_cacheHit() {
        UUID fan = UUID.randomUUID();
        given(cache.exists(ROOM_ID)).willReturn(true);
        given(cache.getMembers(ROOM_ID)).willReturn(Set.of(fan));

        Set<UUID> fans = cacheService.getFans(ROOM_ID, CREATOR_ID);

        assertThat(fans).containsExactly(fan);
        verify(memberRepository, never()).findUserIdsByRoomId(any());
        verify(cache, never()).cacheMembers(any(), any());
    }

    @Test
    @DisplayName("getFans: 캐시 미스면 DB 조회 후 크리에이터를 제외하고 캐시에 적재한다")
    void getFans_cacheMiss_loadsAndCaches() {
        UUID fan = UUID.randomUUID();
        given(cache.exists(ROOM_ID)).willReturn(false);
        given(memberRepository.findUserIdsByRoomId(ROOM_ID)).willReturn(List.of(CREATOR_ID, fan));

        Set<UUID> fans = cacheService.getFans(ROOM_ID, CREATOR_ID);

        // 크리에이터는 브로드캐스트 대상에서 제외
        assertThat(fans).containsExactly(fan);
        verify(cache).cacheMembers(ROOM_ID, Set.of(fan));
    }
}

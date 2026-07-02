package com.fandom.user_service.follow.presentation.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * 커서 기반 페이징 응답.
 *
 * 대량 데이터를 순차 조회할 때 오프셋 페이징(중복/누락, 뒤 페이지 성능 저하)의 문제를 피하기 위해 사용한다.
 * Feed 서비스의 팬아웃/타임라인 조회 등 서비스 간 통신에서 팔로우 ID 목록을 조회하는 데 쓰인다.
 *
 * @param content    이번 페이지 데이터
 * @param nextCursor 다음 페이지 요청 시 넘길 커서. 다음 페이지가 없으면 null.
 *                   (내부적으로 follow.id 기준 커서이며, 클라이언트는 값을 그대로 다음 요청에 전달하면 된다.)
 * @param hasNext    다음 페이지 존재 여부
 */
public record CursorPageResponse<T>(
        List<T> content,
        UUID nextCursor,
        boolean hasNext
) {

    public static <T> CursorPageResponse<T> of(List<T> content, UUID nextCursor, boolean hasNext) {
        return new CursorPageResponse<>(content, nextCursor, hasNext);
    }
}

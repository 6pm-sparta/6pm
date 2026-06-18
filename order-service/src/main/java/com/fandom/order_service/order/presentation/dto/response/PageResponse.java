package com.fandom.order_service.order.presentation.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이지네이션 응답 래퍼. Spring Data의 Page<T>를 직접 직렬화하지 않는 이유:
 * - Page의 기본 JSON 필드명(number, content, totalElements...)이 api 명세서의 page/size 키와 다름
 * - Spring 버전에 따라 Page 직렬화 포맷이 바뀔 수 있어, 응답 스펙을 DTO로 고정해두는 게 안전함
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

package com.fandom.auth_service.auth.infrastructure.client;

import com.fandom.auth_service.auth.infrastructure.client.dto.MemberLookupResponse;
import com.fandom.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * user-service 내부 회원 조회 API를 호출하는 Feign 클라이언트.
 * Eureka에 등록된 user-service를 이름으로 찾아 호출한다(lb://USER-SERVICE).
 */
@FeignClient(name = "user-service")
public interface MemberLookupClient {

    @GetMapping("/internal/v1/members/{email}")
    ApiResponse<MemberLookupResponse> getMemberByEmail(@PathVariable("email") String email);
}

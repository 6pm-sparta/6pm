package com.fandom.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Auth Service.
 * 로그인 및 토큰 발급을 담당한다. 자체 DB는 두지 않고,
 * 회원 정보는 user-service의 내부 조회 API를 OpenFeign으로 호출해 검증한다.
 */
@SpringBootApplication
@EnableFeignClients
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

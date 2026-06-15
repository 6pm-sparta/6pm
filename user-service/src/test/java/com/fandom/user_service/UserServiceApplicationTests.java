package com.fandom.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
// DB 연결 없이 테스트를 실행하도록 설정
@AutoConfigureMockMvc
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}

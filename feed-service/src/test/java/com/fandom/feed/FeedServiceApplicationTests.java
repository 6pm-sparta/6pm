package com.fandom.feed;

import com.fandom.common.auth.HmacUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class FeedServiceApplicationTests {
	@MockitoBean
	private HmacUtils hmacUtils;

	@Test
	void contextLoads() {
	}
}
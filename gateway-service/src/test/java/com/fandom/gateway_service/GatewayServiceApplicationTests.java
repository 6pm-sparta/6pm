package com.fandom.gateway_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false",
		"jwt.secret=6pm-fandom-sns-test-jwt-secret-key-must-be-at-least-32-bytes-long",
		"hmac.secret-key=6pm-fandom-sns-test-hmac-secret-key-must-be-at-least-32-bytes-long"
})
class GatewayServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

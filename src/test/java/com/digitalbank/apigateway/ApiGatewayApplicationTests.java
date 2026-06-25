package com.digitalbank.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.config.import=",
		"spring.cloud.config.enabled=false" })
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}
}

package com.digitalbank.apigateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.config.import=",
		"spring.cloud.config.enabled=false",
		"spring.cloud.gateway.server.webflux.routes[0].id=customer-service-health",
		"spring.cloud.gateway.server.webflux.routes[0].uri=forward:/actuator/health",
		"spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/gateway-test/customer-health" })
class ApiGatewayRouteIntegrationTests {

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void healthEndpointReportsUp() {
		client.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void routeForwardsToConfiguredDownstreamService() {
		client.get()
				.uri("/gateway-test/customer-health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}
}

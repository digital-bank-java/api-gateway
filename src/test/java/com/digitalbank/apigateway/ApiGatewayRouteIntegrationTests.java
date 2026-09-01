package com.digitalbank.apigateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.config.import=",
		"spring.cloud.config.enabled=false",
		"spring.cloud.gateway.server.webflux.routes[0].id=customer-service-health",
		"spring.cloud.gateway.server.webflux.routes[0].uri=forward:/actuator/health",
		"spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/gateway-test/customer-health",
		"spring.cloud.gateway.server.webflux.routes[1].id=unavailable-service-docs",
		"spring.cloud.gateway.server.webflux.routes[1].uri=http://127.0.0.1:1",
		"spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/gateway-test/unavailable-docs",
		"resilience4j.circuitbreaker.configs.gatewayDownstream.minimumNumberOfCalls=1",
		"resilience4j.circuitbreaker.configs.gatewayDownstream.slidingWindowSize=1",
		"resilience4j.circuitbreaker.configs.gatewayDownstream.waitDurationInOpenState=1h",
		"gateway.resilience.timeout.connect-timeout=100",
		"gateway.resilience.timeout.response-timeout=100ms",
		"management.health.redis.enabled=false" })
class ApiGatewayRouteIntegrationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private Environment environment;

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

	@Test
	void unavailableDownstreamDoesNotTakeDownGateway() {
		client.get()
				.uri("/gateway-test/unavailable-docs")
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectHeader().contentType("application/problem+json")
				.expectBody()
				.jsonPath("$.status").isEqualTo(503)
				.jsonPath("$.title").isEqualTo("Downstream service unavailable");

		client.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void unavailableDownstreamDoesNotTripHealthyRoute() {
		client.get()
				.uri("/gateway-test/unavailable-docs")
				.exchange()
				.expectStatus().isEqualTo(503);

		client.get()
				.uri("/gateway-test/customer-health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void internalFallbackPathIsNotAnExposedGatewayEndpoint() {
		client.get()
				.uri("/internal/gateway-fallback")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void retryFilterOnlyTargetsSafeGetRequests() {
		assertEquals("GET,HEAD,OPTIONS", environment
				.getProperty("spring.cloud.gateway.server.webflux.default-filters[0].args.methods"));
		assertEquals("INTERNAL_SERVER_ERROR", environment
				.getProperty("spring.cloud.gateway.server.webflux.default-filters[0].args.statuses[0]"));
		assertEquals("GATEWAY_TIMEOUT", environment
				.getProperty("spring.cloud.gateway.server.webflux.default-filters[0].args.statuses[3]"));
		assertEquals("50ms", environment
				.getProperty("spring.cloud.gateway.server.webflux.default-filters[0].args.backoff.firstBackoff"));
	}
}

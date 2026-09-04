package com.digitalbank.apigateway;

import static java.util.Map.of;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.config.import=",
		"spring.cloud.config.enabled=false",
		"spring.cloud.gateway.server.webflux.routes[0].id=rate-limited-health",
		"spring.cloud.gateway.server.webflux.routes[0].uri=forward:/actuator/health",
		"spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/gateway-test/rate-limited-health",
		"spring.cloud.gateway.server.webflux.routes[0].filters[0]=RequestRateLimiter",
		"spring.cloud.gateway.server.webflux.routes[0].filters[0].args.key-resolver=#{@clientAddressKeyResolver}",
		"spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.replenish-rate=1",
		"spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.burst-capacity=1",
		"spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.requested-tokens=1",
		"management.health.redis.enabled=false" })
@Import(GatewayRateLimitingIntegrationTests.TestRateLimitingConfiguration.class)
@Testcontainers
class GatewayRateLimitingIntegrationTests {

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", redis::getFirstMappedPort);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		applicationEventPublisher.publishEvent(new FilterArgsEvent(this, "rate-limited-health", of(
				"redis-rate-limiter.replenishRate", "1",
				"redis-rate-limiter.burstCapacity", "1",
				"redis-rate-limiter.requestedTokens", "1")));
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void routeRejectsRequestsAfterConfiguredBurstIsConsumed() {
		client.get()
				.uri("/gateway-test/rate-limited-health")
				.exchange()
				.expectStatus().isOk();

		client.get()
				.uri("/gateway-test/rate-limited-health")
				.exchange()
				.expectStatus().isEqualTo(429);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static final class TestRateLimitingConfiguration {

		@Bean
		KeyResolver clientAddressKeyResolver() {
			return exchange -> Mono.just("test-client");
		}
	}
}

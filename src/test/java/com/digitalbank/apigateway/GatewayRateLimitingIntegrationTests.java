package com.digitalbank.apigateway;

import static java.util.Map.of;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
		"spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.replenishRate=1",
		"spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.burstCapacity=1",
		"spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.requestedTokens=1",
		"management.health.redis.enabled=false" })
@Import(GatewayRateLimitingIntegrationTests.TestRateLimitingConfiguration.class)
class GatewayRateLimitingIntegrationTests {

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
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

		@Bean
		@Primary
		RateLimiter<RedisRateLimiter.Config> testRateLimiter() {
			return new DeterministicRateLimiter();
		}
	}

	private static final class DeterministicRateLimiter extends RedisRateLimiter {

		private final AtomicInteger remainingRequests = new AtomicInteger(1);

		private DeterministicRateLimiter() {
			super(1, 1);
		}

		@Override
		public Mono<Response> isAllowed(String routeId, String id) {
			boolean allowed = remainingRequests.getAndUpdate(value -> Math.max(0, value - 1)) > 0;
			return Mono.just(new Response(allowed, of()));
		}
	}
}

package com.digitalbank.apigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.config.import=",
		"spring.cloud.config.enabled=false",
		"spring.cloud.gateway.server.webflux.routes[0].id=unavailable-service",
		"spring.cloud.gateway.server.webflux.routes[0].uri=http://127.0.0.1:1",
		"spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/gateway-test/unavailable",
		"resilience4j.circuitbreaker.configs.gatewayDownstream.waitDurationInOpenState=1h",
		"spring.cloud.gateway.server.webflux.default-filters=",
		"gateway.resilience.circuit-breaker.minimum-number-of-calls=2",
		"gateway.resilience.circuit-breaker.sliding-window-size=2",
		"gateway.resilience.circuit-breaker.failure-rate-threshold=50",
		"gateway.resilience.timeout.connect-timeout=100",
		"gateway.resilience.timeout.response-timeout=50ms",
		"management.health.redis.enabled=false" })
@Import(GatewayResilienceIntegrationTests.TestRouteConfiguration.class)
class GatewayResilienceIntegrationTests {

	private static final DisposableServer SLOW_SERVER = startSlowServer();
	private static final DisposableServer SERVER_ERROR_SERVER = startServerErrorServer();

	@LocalServerPort
	private int port;

	@Autowired
	private CircuitBreakerRegistry registry;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@AfterAll
	static void stopSlowServer() {
		SLOW_SERVER.disposeNow();
		SERVER_ERROR_SERVER.disposeNow();
	}

	@Test
	void configuredCircuitBreakerOpensAfterConfiguredFailures() {
		client.get().uri("/gateway-test/unavailable").exchange().expectStatus().isEqualTo(503);
		client.get().uri("/gateway-test/unavailable").exchange().expectStatus().isEqualTo(503);

		CircuitBreaker breaker = registry.circuitBreaker("gatewayDownstream-unavailable-service");
		assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
		assertEquals(2, breaker.getMetrics().getNumberOfBufferedCalls());
	}

	@Test
	void downstreamServerErrorsOpenCircuitBreaker() {
		client.get().uri("/gateway-test/server-error").exchange().expectStatus().is5xxServerError();
		client.get().uri("/gateway-test/server-error").exchange().expectStatus().is5xxServerError();

		CircuitBreaker breaker = registry.circuitBreaker("gatewayDownstream-server-error-service");
		Awaitility.await().atMost(Duration.ofSeconds(1))
				.untilAsserted(() -> assertEquals(CircuitBreaker.State.OPEN, breaker.getState()));

		client.get().uri("/gateway-test/server-error")
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Downstream service unavailable");
	}

	@Test
	void responseTimeoutReturnsFallbackWithinConfiguredBound() {
		long startedAt = System.nanoTime();

		client.get().uri("/gateway-test/slow")
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectBody()
				.jsonPath("$.title").isEqualTo("Downstream service unavailable");

		long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
		assertTrue(elapsedMillis < 500,
				() -> "The response timeout was not enforced; elapsed milliseconds: " + elapsedMillis);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static final class TestRouteConfiguration {

		@Bean
		RouteLocator slowRoute(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("slow-service", route -> route.path("/gateway-test/slow")
							.uri("http://127.0.0.1:" + SLOW_SERVER.port()))
					.route("server-error-service", route -> route.path("/gateway-test/server-error")
							.uri("http://127.0.0.1:" + SERVER_ERROR_SERVER.port()))
					.build();
		}
	}

	private static DisposableServer startSlowServer() {
		return HttpServer.create()
				.handle((request, response) -> response
						.status(200)
						.sendString(Mono.delay(Duration.ofMillis(750)).map(ignored -> "slow")))
				.bindNow();
	}

	private static DisposableServer startServerErrorServer() {
		return HttpServer.create()
				.handle((request, response) -> response.status(500).send())
				.bindNow();
	}
}

package com.digitalbank.apigateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.config.import=",
		"spring.cloud.config.enabled=false",
		"gateway.security.enabled=true",
		"auth.jwt.issuer=https://issuer.test",
		"management.health.redis.enabled=false" })
@Import(GatewaySecurityIntegrationTests.TestJwtConfiguration.class)
class GatewaySecurityIntegrationTests {

	@Value("${local.server.port}")
	private int port;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void adminRouteWithoutBearerTokenReturnsProblemDetailsUnauthorized() {
		client.get()
				.uri("/admin/v1/customers")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentType("application/problem+json")
				.expectHeader().valueEquals("WWW-Authenticate", "Bearer")
				.expectBody()
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.title").isEqualTo("Authentication required")
				.jsonPath("$.instance").isEqualTo("/admin/v1/customers");
	}

	@Test
	void adminRouteWithInsufficientScopeReturnsProblemDetailsForbidden() {
		client.get()
				.uri("/admin/v1/customers")
				.headers(headers -> headers.setBearerAuth("wrong-scope"))
				.exchange()
				.expectStatus().isForbidden()
				.expectHeader().contentType("application/problem+json")
				.expectBody()
				.jsonPath("$.status").isEqualTo(403)
				.jsonPath("$.title").isEqualTo("Access denied")
				.jsonPath("$.instance").isEqualTo("/admin/v1/customers");
	}

	@Test
	void healthEndpointRemainsPublicWhenGatewaySecurityIsEnabled() {
		client.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static final class TestJwtConfiguration {

		@Bean
		ReactiveJwtDecoder reactiveJwtDecoder() {
			return token -> Mono.just(Jwt.withTokenValue(token)
					.header("alg", "none")
					.claim("scope", "wrong.scope")
					.issuedAt(Instant.now())
					.expiresAt(Instant.now().plusSeconds(300))
					.build());
		}
	}
}

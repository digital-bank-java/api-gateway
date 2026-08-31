package com.digitalbank.apigateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import reactor.core.publisher.Mono;

/** Provides the unauthenticated SIT key used by Redis-backed route limits. */
@Configuration(proxyBeanMethods = false)
class GatewayRateLimitingConfiguration {

	@Bean
	@Primary
	KeyResolver rateLimitKeyResolver() {
		return exchange -> {
			var remoteAddress = exchange.getRequest().getRemoteAddress();
			if (remoteAddress == null || remoteAddress.getAddress() == null) {
				return Mono.empty();
			}
			return Mono.just(remoteAddress.getAddress().getHostAddress());
		};
	}
}

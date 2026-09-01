package com.digitalbank.apigateway;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Provides rate-limit identity and the explicit SIT Redis failure policy. */
@Configuration(proxyBeanMethods = false)
class GatewayRateLimitingConfiguration {

	@Bean
	@ConfigurationProperties("gateway.rate-limit")
	GatewayRateLimitProperties gatewayRateLimitProperties() {
		return new GatewayRateLimitProperties();
	}

	@Bean
	@Primary
	KeyResolver rateLimitKeyResolver(GatewayRateLimitProperties properties) {
		var resolver = new ClientAddressKeyResolver(properties.getTrustedProxies());
		return resolver::resolve;
	}

	@Bean
	@Primary
	RedisRateLimiter failClosedRedisRateLimiter(ReactiveStringRedisTemplate redisTemplate,
			@Qualifier("redisRequestRateLimiterScript") RedisScript<List<Long>> script,
			ConfigurationService configurationService) {
		return new FailClosedRedisRateLimiter(redisTemplate, script, configurationService);
	}

	static final class GatewayRateLimitProperties {
		private List<String> trustedProxies = List.of();

		public List<String> getTrustedProxies() {
			return trustedProxies;
		}

		public void setTrustedProxies(List<String> trustedProxies) {
			this.trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
		}
	}
}

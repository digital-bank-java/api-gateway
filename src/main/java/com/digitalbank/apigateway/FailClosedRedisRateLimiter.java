package com.digitalbank.apigateway;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import reactor.core.publisher.Mono;

/** Converts the Gateway Redis limiter's error sentinel into an explicit deny decision. */
final class FailClosedRedisRateLimiter extends RedisRateLimiter {

	FailClosedRedisRateLimiter(ReactiveStringRedisTemplate redisTemplate,
			RedisScript<java.util.List<Long>> script,
			org.springframework.cloud.gateway.support.ConfigurationService configurationService) {
		super(redisTemplate, script, configurationService);
	}

	@Override
	public Mono<RateLimiter.Response> isAllowed(String routeId, String id) {
		return super.isAllowed(routeId, id).map(FailClosedRedisRateLimiter::applyFailurePolicy);
	}

	static RateLimiter.Response applyFailurePolicy(RateLimiter.Response response) {
		var remaining = response.getHeaders().get(RedisRateLimiter.REMAINING_HEADER);
		if (response.isAllowed() && "-1".equals(remaining)) {
			return new RateLimiter.Response(false, response.getHeaders());
		}
		return response;
	}
}

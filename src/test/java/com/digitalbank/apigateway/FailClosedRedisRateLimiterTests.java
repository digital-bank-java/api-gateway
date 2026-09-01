package com.digitalbank.apigateway;

import static java.util.Map.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

class FailClosedRedisRateLimiterTests {

	@Test
	void convertsRedisErrorSentinelIntoDeniedResponse() {
		var response = new RateLimiter.Response(true,
				of(RedisRateLimiter.REMAINING_HEADER, "-1"));

		var result = FailClosedRedisRateLimiter.applyFailurePolicy(response);

		assertFalse(result.isAllowed());
		assertEquals(response.getHeaders(), result.getHeaders());
	}

	@Test
	void preservesNormalAllowedResponse() {
		var response = new RateLimiter.Response(true,
				of(RedisRateLimiter.REMAINING_HEADER, "19"));

		var result = FailClosedRedisRateLimiter.applyFailurePolicy(response);

		assertTrue(result.isAllowed());
		assertSame(response, result);
	}
}

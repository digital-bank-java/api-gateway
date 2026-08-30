package com.digitalbank.apigateway;

import java.nio.charset.StandardCharsets;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

/** Applies an isolated circuit breaker to each configured downstream route. */
@Component
final class RouteCircuitBreakerGlobalFilter implements GlobalFilter, Ordered {

	private static final String FALLBACK_PATH = "/internal/gateway-fallback";
	private static final String CONFIGURATION_NAME = "gatewayDownstream";
	private static final String BREAKER_NAME_PREFIX = "gatewayDownstream-";
	private static final String FALLBACK_BODY = "{\"type\":\"https://digital-bank-java.local/problems/"
			+ "downstream-unavailable\",\"title\":\"Downstream service unavailable\","
			+ "\"status\":503,\"detail\":\"The requested downstream service is temporarily unavailable.\"}";

	private final CircuitBreakerRegistry registry;
	private final CircuitBreakerConfig downstreamConfiguration;

	RouteCircuitBreakerGlobalFilter(CircuitBreakerRegistry registry) {
		this.registry = registry;
		this.downstreamConfiguration = registry.getConfiguration(CONFIGURATION_NAME)
				.orElse(CircuitBreakerConfig.ofDefaults());
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
		if (route == null || FALLBACK_PATH.equals(exchange.getRequest().getPath().value())) {
			return chain.filter(exchange);
		}

		CircuitBreaker circuitBreaker = registry.circuitBreaker(BREAKER_NAME_PREFIX + route.getId(),
				downstreamConfiguration);
		return chain.filter(exchange)
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.onErrorResume(ignored -> writeFallback(exchange));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	private Mono<Void> writeFallback(ServerWebExchange exchange) {
		if (exchange.getResponse().isCommitted()) {
			return Mono.error(new IllegalStateException("Gateway response was already committed"));
		}

		exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		var buffer = exchange.getResponse().bufferFactory().wrap(FALLBACK_BODY.getBytes(StandardCharsets.UTF_8));
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}
}

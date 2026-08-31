package com.digitalbank.apigateway;

import java.nio.charset.StandardCharsets;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClientResponse;

/** Applies an isolated circuit breaker to each configured downstream route. */
@Component
final class RouteCircuitBreakerGlobalFilter implements GlobalFilter, Ordered {

	private static final String CONFIGURATION_NAME = "gatewayDownstream";
	private static final String BREAKER_NAME_PREFIX = "gatewayDownstream-";
	private static final String FALLBACK_BODY = "{\"type\":\"https://digital-bank-java.local/problems/"
			+ "downstream-unavailable\",\"title\":\"Downstream service unavailable\","
			+ "\"status\":503,\"detail\":\"The requested downstream service is temporarily unavailable.\"}";

	private final CircuitBreakerRegistry registry;

	RouteCircuitBreakerGlobalFilter(CircuitBreakerRegistry registry) {
		this.registry = registry;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
		if (route == null) {
			return chain.filter(exchange);
		}

		CircuitBreaker circuitBreaker = registry.circuitBreaker(BREAKER_NAME_PREFIX + route.getId(),
				CONFIGURATION_NAME);
		return chain.filter(exchange)
				.then(Mono.defer(() -> failOnDownstreamServerError(exchange)))
				.transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
				.onErrorResume(ignored -> fallbackOrPreserveCommittedResponse(exchange));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	private Mono<Void> writeFallback(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		var buffer = exchange.getResponse().bufferFactory().wrap(FALLBACK_BODY.getBytes(StandardCharsets.UTF_8));
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}

	private Mono<Void> fallbackOrPreserveCommittedResponse(ServerWebExchange exchange) {
		return exchange.getResponse().isCommitted() ? Mono.empty() : writeFallback(exchange);
	}

	private Mono<Void> failOnDownstreamServerError(ServerWebExchange exchange) {
		HttpClientResponse response = exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR);
		if (response != null) {
			HttpStatusCode status = HttpStatusCode.valueOf(response.status().code());
			if (status.is5xxServerError()) {
				return Mono.error(new DownstreamServerError(status));
			}
		}
		return Mono.empty();
	}

	private static final class DownstreamServerError extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private DownstreamServerError(HttpStatusCode status) {
			super("Downstream returned HTTP " + status.value());
		}
	}
}

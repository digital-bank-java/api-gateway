package com.digitalbank.apigateway;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/** Adds a bounded correlation identifier to every gateway exchange. */
@Component
@Order(-100)
public final class CorrelationIdWebFilter implements WebFilter {

	static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

	private static final int MAX_CORRELATION_ID_LENGTH = 96;
	private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}");
	private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

	private final String environment;

	public CorrelationIdWebFilter(
			@Value("${digital-bank.service.runtime-profile:${spring.profiles.active:sit}}") String environment) {
		this.environment = environment;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String correlationId = resolveCorrelationId(exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER));
		ServerHttpRequest request = exchange.getRequest().mutate().header(CORRELATION_ID_HEADER, correlationId).build();
		ServerWebExchange correlatedExchange = exchange.mutate().request(request).build();
		correlatedExchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
		long startedAt = System.nanoTime();

		return chain.filter(correlatedExchange).doFinally(signalType -> logCompletion(correlatedExchange, correlationId,
				startedAt, signalType.name()));
	}

	static String resolveCorrelationId(String candidate) {
		if (candidate != null && candidate.length() <= MAX_CORRELATION_ID_LENGTH
				&& SAFE_CORRELATION_ID.matcher(candidate).matches()) {
			return candidate;
		}
		return UUID.randomUUID().toString();
	}

	private void logCompletion(ServerWebExchange exchange, String correlationId, long startedAt, String signal) {
		int status = exchange.getResponse().getStatusCode() == null ? 500
				: exchange.getResponse().getStatusCode().value();
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		String routeId = route == null || route.getId() == null || route.getId().isBlank() ? "unmatched" : route.getId();
		String method = exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name();

		LOGGER.atInfo()
				.addKeyValue("event.name", "gateway.request.completed")
				.addKeyValue("digital_bank.environment", environment)
				.addKeyValue("http.request.method", method)
				.addKeyValue("http.route", routeId)
				.addKeyValue("http.response.status_code", status)
				.addKeyValue("correlation.id", correlationId)
				.addKeyValue("correlation_id", correlationId)
				.addKeyValue("event.duration_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
				.addKeyValue("reactor.signal", signal)
				.log("Gateway request completed");
	}
}

package com.digitalbank.apigateway.security;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "gateway.security.enabled", havingValue = "true")
final class GatewayProblemDetailsWriter {

	private final ObjectMapper objectMapper;

	GatewayProblemDetailsWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	Mono<Void> write(ServerWebExchange exchange, int status, String title, String detail,
			boolean includeAuthenticateHeader) {
		exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		if (includeAuthenticateHeader) {
			exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		}

		Map<String, Object> problem = new LinkedHashMap<>();
		problem.put("type", "https://digital-bank-java.local/problems/authorization-error");
		problem.put("title", title);
		problem.put("status", status);
		problem.put("detail", detail);
		problem.put("instance", exchange.getRequest().getPath().value());

		final byte[] body;
		try {
			body = objectMapper.writeValueAsBytes(problem);
		} catch (JacksonException exception) {
			return Mono.error(exception);
		}
		var buffer = exchange.getResponse().bufferFactory().wrap(body);
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}
}

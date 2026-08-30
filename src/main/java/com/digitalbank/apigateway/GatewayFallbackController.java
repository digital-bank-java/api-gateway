package com.digitalbank.apigateway;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/internal/gateway-fallback")
class GatewayFallbackController {

	@RequestMapping
	ResponseEntity<ProblemDetail> downstreamUnavailable(ServerWebExchange exchange) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The requested downstream service is temporarily unavailable.");
		problem.setTitle("Downstream service unavailable");
		problem.setType(URI.create("https://digital-bank-java.local/problems/downstream-unavailable"));
		problem.setInstance(URI.create(exchange.getRequest().getPath().value()));

		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(problem);
	}
}

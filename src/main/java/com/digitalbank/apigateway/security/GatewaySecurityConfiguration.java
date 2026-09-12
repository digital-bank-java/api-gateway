package com.digitalbank.apigateway.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@ConditionalOnProperty(name = "gateway.security.enabled", havingValue = "true")
class GatewaySecurityConfiguration {

	@Bean
	SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http,
			ServerAuthenticationEntryPoint authenticationEntryPoint,
			ServerAccessDeniedHandler accessDeniedHandler) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(authorize -> authorize
						.pathMatchers(
								"/actuator/health/**",
								"/actuator/info",
								"/auth-service/actuator/health/**",
								"/account-service/actuator/health/**",
								"/config-server/actuator/health/**",
								"/customer-service/actuator/health/**",
								"/ledger-service/actuator/health/**",
								"/mfa-service/actuator/health/**",
								"/notification-service/actuator/health/**",
								"/payment-service/actuator/health/**",
								"/transaction-service/actuator/health/**",
								"/error",
								"/api/v1/auth/login")
							.permitAll()
						.pathMatchers("/api/v1/auth/**")
							.authenticated()
						.pathMatchers(
								"/admin/**",
								"/v3/api-docs/swagger-config",
								"/swagger-ui/**",
								"/swagger-ui.html")
							.hasAuthority("SCOPE_admin.internal")
						.pathMatchers("/api/v1/mfa/**")
							.hasAuthority("SCOPE_mfa.internal")
						.pathMatchers("/internal/v1/transfer-workflows", "/internal/v1/transfer-workflows/**")
							.hasAuthority("SCOPE_transfer.internal")
						.pathMatchers("/internal/v1/payment-instructions", "/internal/v1/payment-instructions/**")
							.hasAuthority("SCOPE_payment.internal")
						.pathMatchers("/api/v1/customers/*/accounts")
							.hasAnyAuthority("SCOPE_account.self", "SCOPE_admin.internal")
						.pathMatchers("/api/v1/customers/**")
							.hasAnyAuthority("SCOPE_customer.self", "SCOPE_admin.internal")
						.pathMatchers("/api/v1/accounts/**")
							.hasAnyAuthority("SCOPE_account.self", "SCOPE_admin.internal")
						.anyExchange().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(withDefaults())
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.build();
	}

	@Bean
	ServerAuthenticationEntryPoint gatewayAuthenticationEntryPoint(GatewayProblemDetailsWriter writer) {
		return (exchange, exception) -> writer.write(exchange, 401, "Authentication required",
				"A valid bearer token is required to access this resource.", true);
	}

	@Bean
	ServerAccessDeniedHandler gatewayAccessDeniedHandler(GatewayProblemDetailsWriter writer) {
		return (exchange, exception) -> writer.write(exchange, 403, "Access denied",
				"The bearer token does not grant the required scope for this resource.", false);
	}
}

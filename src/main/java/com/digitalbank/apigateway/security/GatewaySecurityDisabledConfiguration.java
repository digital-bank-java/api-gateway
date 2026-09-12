package com.digitalbank.apigateway.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "gateway.security.enabled", havingValue = "false")
class GatewaySecurityDisabledConfiguration {

	@Bean
	SecurityWebFilterChain gatewaySecurityDisabledWebFilterChain(ServerHttpSecurity http) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(authorize -> authorize.anyExchange().permitAll())
				.build();
	}
}

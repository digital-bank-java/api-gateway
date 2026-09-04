package com.digitalbank.apigateway.security;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "gateway.security.enabled", havingValue = "true")
class GatewayJwtDecoderConfiguration {

	private static final int MINIMUM_HMAC_KEY_BYTES = 32;

	@Bean
	@ConditionalOnMissingBean(ReactiveJwtDecoder.class)
	ReactiveJwtDecoder gatewayReactiveJwtDecoder(
			@Value("${auth.jwt.issuer:}") String issuer,
			@Value("${auth.jwt.secret:}") String secret,
			@Value("${auth.jwt.jwk-set-uri:}") String jwkSetUri) {
		if (StringUtils.hasText(secret)) {
			return hmacDecoder(secret, issuer);
		}
		if (StringUtils.hasText(jwkSetUri)) {
			return jwkDecoder(jwkSetUri, issuer);
		}
		if (StringUtils.hasText(issuer)) {
			return ReactiveJwtDecoders.fromIssuerLocation(issuer);
		}
		throw new IllegalStateException(
				"Gateway security is enabled but no auth.jwt.secret, auth.jwt.jwk-set-uri, or auth.jwt.issuer is configured");
	}

	private ReactiveJwtDecoder hmacDecoder(String encodedSecret, String issuer) {
		final byte[] secret;
		try {
			secret = Base64.getDecoder().decode(encodedSecret);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("auth.jwt.secret must be a valid Base64 value", exception);
		}
		if (secret.length < MINIMUM_HMAC_KEY_BYTES) {
			throw new IllegalStateException("auth.jwt.secret must decode to at least 32 bytes");
		}
		return configuredDecoder(new SecretKeySpec(secret, "HmacSHA256"), issuer);
	}

	private ReactiveJwtDecoder jwkDecoder(String jwkSetUri, String issuer) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
		if (StringUtils.hasText(issuer)) {
			decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
		}
		return decoder;
	}

	private ReactiveJwtDecoder configuredDecoder(SecretKey key, String issuer) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key).build();
		if (StringUtils.hasText(issuer)) {
			OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator));
		}
		return decoder;
	}
}

package com.digitalbank.apigateway.security;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
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
			@Value("${auth.jwt.jwk-set-uri:}") String jwkSetUri,
			@Value("${auth.jwt.audience:}") String audience,
			@Value("${auth.jwt.token-purpose:}") String tokenPurpose) {
		if (!StringUtils.hasText(audience) || !StringUtils.hasText(tokenPurpose)) {
			throw new IllegalStateException(
					"Gateway security is enabled but auth.jwt.audience and auth.jwt.token-purpose are not configured");
		}
		if (StringUtils.hasText(secret)) {
			return hmacDecoder(secret, issuer, audience, tokenPurpose);
		}
		if (StringUtils.hasText(jwkSetUri)) {
			return jwkDecoder(jwkSetUri, issuer, audience, tokenPurpose);
		}
		if (StringUtils.hasText(issuer)) {
			return configuredDecoder((NimbusReactiveJwtDecoder) ReactiveJwtDecoders.fromIssuerLocation(issuer), issuer,
					audience, tokenPurpose);
		}
		throw new IllegalStateException(
				"Gateway security is enabled but no auth.jwt.secret, auth.jwt.jwk-set-uri, or auth.jwt.issuer is configured");
	}

	private ReactiveJwtDecoder hmacDecoder(String encodedSecret, String issuer, String audience, String tokenPurpose) {
		final byte[] secret;
		try {
			secret = Base64.getDecoder().decode(encodedSecret);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("auth.jwt.secret must be a valid Base64 value", exception);
		}
		if (secret.length < MINIMUM_HMAC_KEY_BYTES) {
			throw new IllegalStateException("auth.jwt.secret must decode to at least 32 bytes");
		}
		return configuredDecoder(new SecretKeySpec(secret, "HmacSHA256"), issuer, audience, tokenPurpose);
	}

	private ReactiveJwtDecoder jwkDecoder(String jwkSetUri, String issuer, String audience, String tokenPurpose) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
		return configuredDecoder(decoder, issuer, audience, tokenPurpose);
	}

	private ReactiveJwtDecoder configuredDecoder(SecretKey key, String issuer, String audience, String tokenPurpose) {
		NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key).build();
		return configuredDecoder(decoder, issuer, audience, tokenPurpose);
	}

	private ReactiveJwtDecoder configuredDecoder(
			NimbusReactiveJwtDecoder decoder, String issuer, String audience, String tokenPurpose) {
		OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
				"aud", values -> values != null && values.contains(audience));
		OAuth2TokenValidator<Jwt> purposeValidator = new JwtClaimValidator<String>(
				"token_purpose", tokenPurpose::equals);
		if (StringUtils.hasText(issuer)) {
			OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator, purposeValidator));
		} else {
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(audienceValidator, purposeValidator));
		}
		return decoder;
	}
}

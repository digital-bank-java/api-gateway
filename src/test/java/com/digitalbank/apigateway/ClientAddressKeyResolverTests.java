package com.digitalbank.apigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ClientAddressKeyResolverTests {

	@Test
	void usesSocketPeerWhenForwardingPeerIsNotTrusted() {
		var resolver = new ClientAddressKeyResolver(List.of("10.0.0.0/8"));
		var exchange = exchange("192.0.2.10", "198.51.100.20");

		assertEquals("192.0.2.10", resolver.resolve(exchange).block());
	}

	@Test
	void resolvesFirstUntrustedAddressFromTrustedProxyChain() {
		var resolver = new ClientAddressKeyResolver(List.of("10.0.0.0/8"));
		var exchange = exchange("10.1.2.3", "198.51.100.20, 10.9.8.7");

		assertEquals("198.51.100.20", resolver.resolve(exchange).block());
	}

	@Test
	void rejectsMalformedForwardingChainFromTrustedPeer() {
		var resolver = new ClientAddressKeyResolver(List.of("10.0.0.0/8"));
		var exchange = exchange("10.1.2.3", "not-an-ip");

		assertNull(resolver.resolve(exchange).block());
	}

	private static MockServerWebExchange exchange(String peer, String forwardedFor) {
		var request = MockServerHttpRequest.get("/")
				.remoteAddress(new InetSocketAddress(peer, 1234))
				.header("X-Forwarded-For", forwardedFor)
				.build();
		return MockServerWebExchange.from(request);
	}
}

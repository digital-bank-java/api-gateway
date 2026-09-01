package com.digitalbank.apigateway;

import java.net.InetAddress;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/** Resolves a client address without trusting forwarding headers from an untrusted peer. */
final class ClientAddressKeyResolver {

	private static final String X_FORWARDED_FOR = "X-Forwarded-For";

	private final List<IpNetwork> trustedProxies;

	ClientAddressKeyResolver(List<String> trustedProxyDefinitions) {
		trustedProxies = trustedProxyDefinitions.stream()
				.map(IpNetwork::parse)
				.toList();
	}

	Mono<String> resolve(ServerWebExchange exchange) {
		var remoteAddress = exchange.getRequest().getRemoteAddress();
		if (remoteAddress == null || remoteAddress.getAddress() == null) {
			return Mono.empty();
		}

		var peer = remoteAddress.getAddress();
		if (!isTrusted(peer)) {
			return Mono.just(peer.getHostAddress());
		}

		return forwardedClientAddress(exchange.getRequest().getHeaders(), peer);
	}

	private Mono<String> forwardedClientAddress(HttpHeaders headers, InetAddress peer) {
		var value = headers.getFirst(X_FORWARDED_FOR);
		if (value == null || value.isBlank()) {
			return Mono.just(peer.getHostAddress());
		}

		var addresses = value.split(",", -1);
		for (var index = addresses.length - 1; index >= 0; index--) {
			var address = parseAddress(addresses[index]);
			if (address == null) {
				return Mono.empty();
			}
			if (!isTrusted(address)) {
				return Mono.just(address.getHostAddress());
			}
		}
		return Mono.just(peer.getHostAddress());
	}

	private boolean isTrusted(InetAddress address) {
		return trustedProxies.stream().anyMatch(network -> network.contains(address));
	}

	private static InetAddress parseAddress(String value) {
		var trimmed = value.trim();
		if (trimmed.isBlank() || !trimmed.matches("[0-9a-fA-F:.]+")) {
			return null;
		}
		try {
			return InetAddress.getByName(trimmed);
		}
		catch (java.net.UnknownHostException exception) {
			return null;
		}
	}

	private record IpNetwork(InetAddress networkAddress, int prefixLength) {

		static IpNetwork parse(String definition) {
			var parts = definition.trim().split("/", 2);
			if (parts.length == 0 || parts[0].isBlank()) {
				throw new IllegalArgumentException("Trusted proxy address must not be blank");
			}

			var address = parseAddress(parts[0]);
			if (address == null) {
				throw new IllegalArgumentException("Invalid trusted proxy address: " + definition);
			}
			var addressBits = address.getAddress().length * 8;
			var prefix = parts.length == 1 ? addressBits : parsePrefix(parts[1], definition);
			if (prefix < 0 || prefix > addressBits) {
				throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + definition);
			}

			return new IpNetwork(mask(address, prefix), prefix);
		}

		boolean contains(InetAddress address) {
			var candidate = address.getAddress();
			var network = networkAddress.getAddress();
			if (candidate.length != network.length) {
				return false;
			}

			var completeBytes = prefixLength / 8;
			var remainingBits = prefixLength % 8;
			for (var index = 0; index < completeBytes; index++) {
				if (candidate[index] != network[index]) {
					return false;
				}
			}
			if (remainingBits == 0) {
				return true;
			}

			var mask = (byte) (0xff << (8 - remainingBits));
			return (candidate[completeBytes] & mask) == (network[completeBytes] & mask);
		}

		private static int parsePrefix(String value, String definition) {
			try {
				return Integer.parseInt(value);
			}
			catch (NumberFormatException exception) {
				throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + definition, exception);
			}
		}

		private static InetAddress mask(InetAddress address, int prefix) {
			var bytes = address.getAddress();
			var fullBytes = prefix / 8;
			var remainingBits = prefix % 8;
			if (remainingBits > 0 && fullBytes < bytes.length) {
				bytes[fullBytes] = (byte) (bytes[fullBytes] & (0xff << (8 - remainingBits)));
				fullBytes++;
			}
			while (fullBytes < bytes.length) {
				bytes[fullBytes++] = 0;
			}
			try {
				return InetAddress.getByAddress(bytes);
			}
			catch (java.net.UnknownHostException exception) {
				throw new IllegalArgumentException("Invalid trusted proxy address", exception);
			}
		}
	}
}

# API Gateway

Spring Cloud Gateway entry point for the Digital Bank Java platform.

## Responsibilities

- Provide the platform HTTP entry point for client-facing API traffic.
- Route requests to internal services through Kubernetes Service DNS names.
- Keep downstream services private inside the cluster for normal manual testing.
- Host cross-cutting gateway policies such as route-level rate limits and downstream resilience.
- Propagate a bounded correlation ID and emit structured request-completion logs without logging sensitive request data.

## Non-Responsibilities

- Business-domain logic.
- Persistence.
- Service discovery through Eureka.
- Domain authentication and token issuance. The gateway validates bearer tokens and applies route-level authorization.

## Runtime Model

The gateway is a Spring Cloud Config client. Routes are loaded from Config Server so SIT, UAT, and production can use environment-specific route targets without rebuilding the image.

SIT routes use Kubernetes Service DNS names, not Pod IPs. Current routes cover customer and account APIs, selected service health endpoints, the centralized Swagger UI, and aggregated OpenAPI documents. The canonical route definitions live in `config-repo/api-gateway`.

Normal API access enters through the gateway. Internal service ports are used only for Kubernetes traffic and explicit debugging procedures.

## Prerequisites

- Java 21.
- Docker Desktop for image builds and local SIT.
- `kubectl` configured for the `docker-desktop` context.
- Helm 4.
- Config Server and the required downstream SIT services for integrated routing tests.

The Maven Wrapper is included, so a global Maven installation is not required.

## Test And Quality Gate

```bash
./mvnw test
./mvnw verify
```

`./mvnw test` runs the fast test phase. `./mvnw verify` is the CI-equivalent Maven quality gate.

## Run From A Workstation

SIT is the supported lowest runtime environment. The API Gateway must be deployed in SIT for full routing verification because its `sit` configuration contains Kubernetes Service DNS names such as `http://customer-service:8081`; those names are not resolvable by a JVM on the workstation.

You can start an unconfigured workstation instance only for isolated health or breakpoint checks:

```bash
./mvnw spring-boot:run
```

Health:

```bash
curl --fail http://localhost:8080/actuator/health
```

For integrated gateway routing, deploy the Gateway in SIT and use the API Gateway port-forward shown below. A workstation Gateway process is not a Kubernetes Service endpoint.

## Build Image

```bash
docker build --tag digital-bank-java/api-gateway:0.0.3 .
```

The image runs as numeric non-root user and group `10001:10001`.

## Deploy To Local SIT

```bash
helm lint helm --values helm/values-sit.yaml

helm template api-gateway helm --values helm/values-sit.yaml |
  kubectl apply --dry-run=client -f -

helm upgrade --install api-gateway helm \
  --namespace digital-bank-sit \
  --create-namespace \
  --values helm/values-sit.yaml \
  --wait \
  --timeout 5m
```

Port-forward the gateway for workstation testing. On machines where port `8080` is already occupied, use `18080:8080` and substitute `18080` in the requests below.

```bash
kubectl port-forward -n digital-bank-sit svc/api-gateway 8080:8080
```

Then call gateway routes through:

```text
http://localhost:8080
```

Verify representative platform endpoints:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/config-server/actuator/health
curl --fail http://localhost:8080/customer-service/actuator/health
curl --fail http://localhost:8080/account-service/actuator/health
curl --fail http://localhost:8080/v3/api-docs/swagger-config
```

The centralized internal API documentation UI is available at:

```text
http://localhost:8080/admin/docs/swagger-ui.html
```

## Correlation And Request Logs

Every gateway exchange receives an `X-Correlation-ID` response header. A caller-provided value is reused only when it starts with an ASCII letter or digit and contains at most 96 ASCII letters, digits, `.`, `_`, `:`, or `-` characters. Invalid, blank, or oversized values are replaced with a generated UUID. The same value is added to the downstream request so service logs can correlate the exchange.

The gateway emits one ECS-formatted JSON completion event per exchange. It contains the service, environment, method, Gateway route ID, response status, correlation ID, duration, and reactive completion signal. Authorization headers, cookies, tokens, request bodies, account data, customer data, and payment data are deliberately excluded.

## Gateway Authorization And Environment Promotion

The gateway is an internal Kubernetes `ClusterIP` Service in SIT. Authentication is enabled by default and validates JWTs before enforcing these scopes. A local test must explicitly set `GATEWAY_SECURITY_ENABLED=false` to use the bypass configuration; do not use that bypass in a deployed environment.

- `admin.internal` for `/admin/**`, centralized API documentation, and administrative access to customer/account resources.
- `customer.self` for a customer profile whose UUID matches the JWT `sub` claim.
- `account.self` for account resources owned by the customer UUID in the JWT `sub` claim.
- `mfa.internal` for `/api/v1/mfa/**`.
- `transfer.internal` for `/internal/v1/transfer-workflows/**`.
- `payment.internal` for `/internal/v1/payment-instructions/**`.

Customer and account APIs require an ownership scope, not merely an authenticated token. Health endpoints and Auth login remain public. Requests that do not authenticate receive `401` Problem Details; authenticated requests without the required scope or ownership receive `403` Problem Details. The current SIT fixture is the `transfer-orchestrator` administrative identity and retains `admin.internal`; subject-to-customer UUID mapping is a temporary boundary until a customer identity provider supplies explicit customer claims.

SIT uses a base64-encoded HMAC secret supplied through the Kubernetes `auth-service-secrets` Secret. UAT and PROD should use the same application contract with an AWS-managed secret or an OIDC/JWK issuer; no token secret belongs in Git. The chart fails closed by default when an environment does not provide an explicit security override.

Circuit breaking and safe-read retries are independent availability controls. They do not replace service authorization, idempotency keys, transactional guarantees, or audit controls.

## Redis-backed Rate Limiting

Public customer and account routes in SIT use the shared Redis service for route-level token-bucket limits. The current SIT policy allows 10 requested tokens per second with a burst capacity of 20 and one token per request. Requests that exceed the available bucket receive `429 Too Many Requests`.

The policy is stored in `config-repo/api-gateway/api-gateway-sit.yml`, so limits can be tuned without rebuilding the gateway image. A gateway rollout is required after changing route configuration because route definitions are loaded during startup.

The current unauthenticated SIT key is the request client IP address. With no trusted proxies configured, the gateway uses the socket peer and ignores `X-Forwarded-For`, so a client cannot spoof its rate-limit identity by sending that header. When the gateway is placed behind an approved ingress or load balancer, configure its proxy IP addresses or CIDR ranges through `gateway.rate-limit.trusted-proxies`; only then will the gateway parse the forwarding chain, from right to left, and select the first untrusted address. Once gateway authentication exists, the key should be changed to a trusted authenticated subject or tenant identity. Do not use arbitrary client-supplied headers as production rate-limit keys.

Redis is a shared coordination dependency: all gateway replicas must use the same Redis service for consistent limits. Local SIT uses the in-cluster `redis` Service; UAT and production should use a managed Redis-compatible service with authentication, encryption, replication, failover, backups, and monitoring.

The gateway applies a fail-closed policy to Redis rate-limit errors. Spring Cloud Gateway's Redis limiter uses a remaining-token value of `-1` when its Redis script cannot complete; this gateway converts that sentinel into a denied decision instead of silently allowing traffic. Redis health, error rates, and resulting `429` responses must be monitored so an unavailable limiter is restored quickly. This policy is intentionally local/SIT-safe and should be reviewed with the production platform team before promotion.

## Security And Environment Promotion

The gateway is an internal Kubernetes `ClusterIP` Service in SIT. Circuit breaking, safe-read retries, Redis-backed rate limiting, authentication, and authorization are cross-cutting controls. They do not replace service-level idempotency, transactional guarantees, or audit controls.

## Downstream Resilience

The gateway applies a Resilience4j circuit breaker to downstream routes and returns a stable `503 Service Unavailable` Problem Details response when a downstream service cannot be reached. The fallback does not expose downstream hostnames or exception details.

The built-in Gateway retry filter is restricted to `GET`, `HEAD`, and `OPTIONS` requests and the explicit upstream statuses `500`, `502`, `503`, and `504`. It allows at most two retries with a bounded exponential backoff from 50 ms to 250 ms. POST, PUT, PATCH, and DELETE requests are deliberately excluded because retrying a mutation can duplicate a business operation. Retry count and circuit-breaker thresholds are property-driven and can be overridden through the environment-specific Config Server repository later.

The retry and circuit-breaker policies are availability controls, not replacements for idempotency keys, transactional guarantees, or service-level authorization.

The same application artifact is promoted through `sit`, `uat`, and `prod`. Environment-specific routes and infrastructure addresses are supplied through Config Server and deployment configuration. Do not commit credentials, tokens, or production endpoints.

## CI And Contribution Workflow

Pull requests and changes to `main` run Maven verification, Helm lint/rendering, and a non-root container smoke test. Use a tracked issue, dedicated branch, and pull request for every change. See the organization [README standard](https://github.com/digital-bank-java/.github/blob/main/docs/readme-standard.md) and [platform conventions](https://github.com/digital-bank-java/.github/blob/main/docs/platform-conventions.md).

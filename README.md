# API Gateway

Spring Cloud Gateway entry point for the Digital Bank Java platform.

## Responsibilities

- Provide the platform HTTP entry point for client-facing API traffic.
- Route requests to internal services through Kubernetes Service DNS names.
- Keep downstream services private inside the cluster for normal manual testing.
- Host cross-cutting gateway policies such as route-level rate limits and downstream resilience.
- Leave authentication, authorization, and correlation propagation to their planned security slices.

## Non-Responsibilities

- Business-domain logic.
- Persistence.
- Service discovery through Eureka.
- Authentication implementation in the initial slice.

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

## Redis-backed Rate Limiting

Public customer and account routes in SIT use the shared Redis service for route-level token-bucket limits. The current SIT policy allows 10 requested tokens per second with a burst capacity of 20 and one token per request. Requests that exceed the available bucket receive `429 Too Many Requests`.

The policy is stored in `config-repo/api-gateway/api-gateway-sit.yml`, so limits can be tuned without rebuilding the gateway image. A gateway rollout is required after changing route configuration because route definitions are loaded during startup.

The current unauthenticated SIT key is the request remote IP address. Port-forwarded workstation requests therefore share one bucket. Once gateway authentication exists, the key should be changed to a trusted authenticated subject or tenant identity. Do not use arbitrary client-supplied headers as production rate-limit keys.

Redis is a shared coordination dependency: all gateway replicas must use the same Redis service for consistent limits. Local SIT uses the in-cluster `redis` Service; UAT and production should use a managed Redis-compatible service with authentication, encryption, replication, failover, backups, and monitoring.

## Security And Environment Promotion

The gateway is an internal Kubernetes `ClusterIP` Service in SIT. Circuit breaking, safe-read retries, and Redis-backed rate limiting are implemented as cross-cutting availability controls. Authentication, authorization, and correlation propagation remain planned capabilities.

## Downstream Resilience

The gateway applies a Resilience4j circuit breaker to downstream routes and returns a stable `503 Service Unavailable` Problem Details response when a downstream service cannot be reached. The fallback does not expose downstream hostnames or exception details.

The built-in Gateway retry filter is restricted to `GET` requests and server-error responses. POST, PUT, PATCH, and DELETE requests are deliberately excluded because retrying a mutation can duplicate a business operation. Retry count and circuit-breaker thresholds are property-driven and can be overridden through the environment-specific Config Server repository later.

The retry and circuit-breaker policies are availability controls, not replacements for idempotency keys, transactional guarantees, or service-level authorization.

The same application artifact is promoted through `sit`, `uat`, and `prod`. Environment-specific routes and infrastructure addresses are supplied through Config Server and deployment configuration. Do not commit credentials, tokens, or production endpoints.

## CI And Contribution Workflow

Pull requests and changes to `main` run Maven verification, Helm lint/rendering, and a non-root container smoke test. Use a tracked issue, dedicated branch, and pull request for every change. See the organization [README standard](https://github.com/digital-bank-java/.github/blob/main/docs/readme-standard.md) and [platform conventions](https://github.com/digital-bank-java/.github/blob/main/docs/platform-conventions.md).

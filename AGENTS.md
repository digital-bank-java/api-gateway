# AGENTS.md

## Repository Purpose

`api-gateway` is the HTTP entry point for the platform.

It routes public, admin, and documentation traffic to downstream services.

## Current Responsibilities

- route customer and account APIs
- route admin query APIs
- aggregate or expose internal documentation access paths
- provide the main local SIT access point through a single port-forward

## Current Non-Responsibilities

- business-domain logic
- persistence
- service-owned validation rules
- direct ownership of downstream configuration values

## Key Commands

```bash
./mvnw test
./mvnw verify
./mvnw spring-boot:run
docker build -t digital-bank-java/api-gateway:<tag> .
helm lint helm --strict
```

## Runtime Notes

- Default service port: `8080`
- Gateway routes depend on `config-repo`
- Local SIT verification usually starts with:

```bash
kubectl port-forward svc/api-gateway 8080:8080 -n digital-bank-sit
```

## Documentation Notes

- Centralized Swagger/OpenAPI access should be exposed here, not by telling users to hit each downstream service directly.
- Admin documentation routes are internal tooling and should stay clearly separated from public customer APIs.
- Gateway authorization is feature-flagged with `gateway.security.enabled`; SIT enables it through Helm and injects the JWT secret from Kubernetes Secret.
- Keep JWT validation at the gateway aligned with downstream scopes: `admin.internal`, `mfa.internal`, `transaction.internal`, and `payment.internal`.

## Working Rules

- Keep routing explicit.
- Avoid embedding service business rules in gateway code.
- When adding a downstream route, link the supporting downstream PR if the gateway change depends on it.

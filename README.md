# API Gateway

Spring Cloud Gateway entry point for the Digital Bank Java platform.

## Responsibilities

- Provide the platform HTTP entry point for client-facing API traffic.
- Route requests to internal services through Kubernetes Service DNS names.
- Keep downstream services private inside the cluster for normal manual testing.
- Host future cross-cutting gateway policies such as rate limits, auth enforcement, and correlation propagation.

## Non-Responsibilities

- Business-domain logic.
- Persistence.
- Service discovery through Eureka.
- Authentication implementation in the initial slice.

## Runtime Model

The gateway is a Spring Cloud Config client. Routes are intended to come from Config Server so SIT, UAT, and production can use environment-specific route targets without rebuilding the image.

Initial SIT route target:

```text
http://customer-service:8081
```

This is a Kubernetes Service DNS name, not a Pod IP.

## Test

```bash
./mvnw test
```

## Run Locally

Start without Config Server when you only need local health checks:

```bash
./mvnw spring-boot:run
```

Health:

```bash
curl --fail http://localhost:8080/actuator/health
```

Start with Config Server when you want route configuration loaded from `config-repo`:

```bash
SPRING_CONFIG_IMPORT=configserver:http://localhost:8888 \
SPRING_PROFILES_ACTIVE=local \
./mvnw spring-boot:run
```

## Build Image

```bash
docker build --tag digital-bank-java/api-gateway:0.0.1 .
```

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

Port-forward the gateway for workstation testing:

```bash
kubectl port-forward -n digital-bank-sit svc/api-gateway 8080:8080
```

Then call gateway routes through:

```text
http://localhost:8080
```

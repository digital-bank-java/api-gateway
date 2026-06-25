FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode clean package

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --gid 10001 spring \
    && useradd --uid 10001 --gid 10001 --system spring

WORKDIR /app

ENV XDG_CONFIG_HOME=/tmp/.config

COPY --from=builder --chown=10001:10001 \
    /workspace/target/api-gateway-*.jar app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

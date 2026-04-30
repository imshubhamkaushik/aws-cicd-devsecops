# Multi-stage Dockerfile for Product Service
# ---------- Stage 1:Build ----------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Install parent POM so the service POM can resolve it
COPY pom.xml ./pom.xml
RUN mvn -B -N install

# Now copy and build the service
COPY product-svc/pom.xml product-svc/pom.xml
COPY product-svc/src product-svc/src
RUN mvn -B clean package -DskipTests -f product-svc/pom.xml

# ---------- Stage 2: App ----------
FROM gcr.io/distroless/java21-debian13:nonroot AS app
WORKDIR /app

# Copy only the JAR from build stage
COPY --from=build /app/product-svc/target/product-svc.jar product-svc.jar
EXPOSE 8082

# Run the JAR
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:InitialRAMPercentage=50", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/product-svc.jar"]

# ---------- Stage 3: Migration ----------
# This image is used exclusively by the copy-migrations init container in product.yaml. It is never run as the main application container.
# Alpine provides /bin/sh so the init container can execute: cp -r /flyway/sql/. /shared/sql/
# SQL files are copied directly from the build source tree (not extracted from the JAR) so they are always identical to what Spring Boot resolves from the classpath at runtime.
FROM alpine:3.20 AS migrations
COPY --from=build /app/product-svc/src/main/resources/db/migration/ /flyway/sql/
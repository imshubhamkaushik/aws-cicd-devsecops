# Multi-stage Dockerfile for User Service
# ---------- Build Stage ----------
FROM maven:3.9.10-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy Maven project files
COPY pom.xml .
COPY src ./src

# Build the Spring Boot fat JAR (skip tests for faster build)
RUN mvn -B clean package -DskipTests

# ---------- Runtime Stage ----------
FROM gcr.io/distroless/java21-debian13:nonroot
WORKDIR /app

# Copy only the JAR from build stage
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081

# Run the JAR
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:InitialRAMPercentage=50", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]

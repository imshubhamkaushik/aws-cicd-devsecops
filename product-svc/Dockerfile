# Multi-stage Dockerfile for Product Service
# ---------- Build Stage ----------
FROM maven:3.9.10-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Install parent POM so the service POM can resolve it
COPY pom.xml ./pom.xml
RUN mvn -B -N install

# Now copy and build the service
COPY product-svc/pom.xml product-svc/pom.xml
COPY product-svc/src product-svc/src
RUN mvn -B clean package -DskipTests -f product-svc/pom.xml

# ---------- Runtime Stage ----------
FROM gcr.io/distroless/java21-debian13:nonroot
WORKDIR /app

# Copy only the JAR from build stage
COPY --from=build /app/product-svc/target/*.jar app.jar
EXPOSE 8082

# Run the JAR
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:InitialRAMPercentage=50", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]

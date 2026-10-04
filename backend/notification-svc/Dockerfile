# Multi-stage build for notification-svc. Build context is the repo root:
#   docker build -f backend/notification-svc/Dockerfile .
#
# security-common (shared JWT/CORS/rate-limit library) is installed into the local Maven
# repository in the same RUN that builds the service, because the BuildKit cache mount
# replaces /root/.m2 for that RUN and anything installed in an earlier layer would be hidden.
# The cache id is per service so parallel builds never share a repository. Nothing in the
# cache mount ends up in the image.
FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml ./pom.xml
COPY backend/security-common/pom.xml security-common/pom.xml
COPY backend/security-common/src security-common/src
COPY backend/notification-svc/pom.xml notification-svc/pom.xml
COPY backend/notification-svc/src notification-svc/src

RUN --mount=type=cache,id=catalogix-m2-notification-svc,target=/root/.m2 \
    mvn -B -N install \
    && mvn -B -f security-common/pom.xml clean install -DskipTests \
    && mvn -B -f notification-svc/pom.xml clean package -DskipTests

# ---------- Stage 2: Runtime ----------
FROM gcr.io/distroless/java21-debian13:nonroot AS runtime
WORKDIR /app

COPY --from=build /app/notification-svc/target/notification-svc.jar notification-svc.jar
EXPOSE 11008

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:InitialRAMPercentage=50", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/notification-svc.jar"]

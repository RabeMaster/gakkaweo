# Stage 1 — Builder
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2 — Runtime
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache tzdata \
    && addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup

ENV TZ=UTC

WORKDIR /app

RUN mkdir -p /data/uploads/profiles && chown -R appuser:appgroup /data

COPY --from=builder --chown=appuser:appgroup /build/build/libs/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

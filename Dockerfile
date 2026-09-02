FROM gradle:8.14.4-jdk21 AS build

WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY build-logic/ build-logic/
COPY shared/ shared/
COPY modules/ modules/
COPY app/ app/

RUN ./gradlew :app:bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system tino \
    && useradd --system --gid tino --home-dir /app --no-create-home tino

WORKDIR /app
COPY --from=build /workspace/app/build/libs/*.jar /app/app.jar

RUN chown -R tino:tino /app
USER tino

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
    CMD curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

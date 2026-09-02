FROM gradle:8.14.4-jdk21 AS extension

WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY build-logic/ build-logic/
COPY keycloak-extension/ keycloak-extension/

RUN ./gradlew :keycloak-extension:jar --no-daemon --console=plain

FROM quay.io/keycloak/keycloak:26.3

COPY --from=extension /workspace/keycloak-extension/build/libs/keycloak-extension.jar /opt/keycloak/providers/tino-otp.jar
RUN /opt/keycloak/bin/kc.sh build

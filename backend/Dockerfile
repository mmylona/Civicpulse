# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q package -DskipTests \
 && java -Djarmode=tools -jar target/civicpulse.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S civicpulse && adduser -S -G civicpulse -u 10001 civicpulse
WORKDIR /app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER 10001
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- http://127.0.0.1:8081/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

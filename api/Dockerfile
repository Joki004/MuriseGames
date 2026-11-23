# syntax=docker/dockerfile:1
# MuriseGames API: Spring Boot on Java 21.
# Build context: the api/ directory (Railway: Root Directory = /api).

# ---- Build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Gradle wrapper and build scripts first, so dependency downloads are cached.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# Tests run in CI; the image build only packages the app.
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/app.jar

# ---- Run ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 murise
COPY --from=build /workspace/app.jar /app/app.jar
USER murise

# Railway sets PORT; application.yaml reads it (default 8080).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

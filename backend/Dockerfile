# syntax=docker/dockerfile:1

# ---- Stage 1: build the Spring Boot fat jar ----
# The build runs from the repository root context so the Gradle wrapper and
# settings.gradle.kts (multi-module) are available.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy Gradle wrapper + build scripts first for better layer caching.
COPY gradlew settings.gradle.kts ./
COPY gradle ./gradle
COPY backend/build.gradle.kts ./backend/build.gradle.kts
RUN chmod +x gradlew && ./gradlew --version

# Now copy sources and build the runnable jar (skip tests — they need Docker/testcontainers).
COPY backend/src ./backend/src
RUN ./gradlew :backend:bootJar -x test --no-daemon \
    && cp "$(ls backend/build/libs/*.jar | grep -v plain | head -n 1)" /workspace/app.jar

# ---- Stage 2: minimal JRE runtime ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Non-root runtime user.
RUN useradd --system --uid 10001 appuser \
    && mkdir -p /data/books /data/imports \
    && chown -R appuser:appuser /data
USER appuser

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

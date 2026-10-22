# ---------- Stage 1: build the jar ----------
# Full JDK 21: has the compiler that Gradle needs.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only what the build needs.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

# Windows doesn't keep the "executable" flag, so set it here.
RUN chmod +x gradlew

# Build the runnable jar. bootJar = no tests (they need Docker, which isn't available here).
# The cache mount keeps Gradle and the downloaded libraries between builds,
# without putting them in the image.
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

# ---------- Stage 2: run the jar ----------
# Only the Java runtime (JRE): no compiler, no Gradle, no source code.
FROM eclipse-temurin:21-jre
WORKDIR /app

# A normal user, so the app doesn't run as root.
RUN groupadd --system spring && useradd --system --gid spring spring

# Take ONLY the jar from stage 1. Everything else in stage 1 is thrown away.
COPY --from=build /workspace/build/libs/*.jar app.jar

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
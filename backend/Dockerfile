# Stage 1: Build
FROM gradle:8.5-jdk21-alpine AS build

WORKDIR /app

# Copy source files
COPY build.gradle settings.gradle ./
COPY src ./src

# Build application
RUN gradle bootJar --no-daemon --stacktrace

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]

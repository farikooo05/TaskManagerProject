# 1. Build stage
FROM gradle:8.5-jdk21 AS builder
WORKDIR /home/app
COPY . .
RUN gradle build -x test

# 2. Runtime stage
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=builder /home/app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

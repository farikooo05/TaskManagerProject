###############################################
# 1. Build frontend (React + Vite)
###############################################
FROM node:20 AS frontend-builder
WORKDIR /app/frontend

COPY TaskManagerFrontend/package*.json ./

RUN npm ci --include=dev

COPY TaskManagerFrontend .

# FIX vite permission issue
RUN chmod +x node_modules/.bin/vite

RUN npm run build



###############################################
# 2. Build backend (Spring Boot + Gradle)
###############################################
FROM gradle:8.5-jdk21 AS backend-builder
WORKDIR /app

# Copy backend project (root)
COPY . .

# Copy frontend build into Spring Boot static folder
COPY --from=frontend-builder /app/frontend/dist ./src/main/resources/static/

RUN gradle clean bootJar --no-daemon

###############################################
# 3. Final runtime image
###############################################
FROM eclipse-temurin:21-jre
WORKDIR /app

EXPOSE 8080

COPY --from=backend-builder /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

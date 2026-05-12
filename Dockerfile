# Stage 1: Build using a full Maven image (Bypasses local mvnw issues)
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
# We use 'mvn' directly, not './mvnw'
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
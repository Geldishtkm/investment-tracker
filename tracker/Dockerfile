# Multi-stage build for better reliability
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies (this layer will be cached)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jdk-jre-alpine

# Install curl for health check
RUN apk add --no-cache curl

# Set working directory
WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/target/tracker-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Simple health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the application
CMD ["java", "-jar", "app.jar"]

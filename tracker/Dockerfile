# Use OpenJDK 21
FROM openjdk:21-jdk-slim

# Set working directory
WORKDIR /app

# Install necessary packages
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make Maven wrapper executable and verify
RUN chmod +x mvnw && \
    ./mvnw --version

# Copy source code
COPY src src

# Build the application with better error handling
RUN ./mvnw clean package -DskipTests -X || \
    (echo "Maven build failed, trying alternative approach..." && \
     ./mvnw clean package -DskipTests --batch-mode --quiet || \
     (echo "Build still failed, checking Maven wrapper..." && \
      ls -la && \
      cat mvnw | head -5 && \
      exit 1))

# Expose port
EXPOSE 8080

# Simple health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the application
CMD ["java", "-jar", "target/tracker-0.0.1-SNAPSHOT.jar"]

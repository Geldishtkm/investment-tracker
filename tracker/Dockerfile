# ==== DIGITALOCEAN OPTIMIZED DOCKERFILE (JAVA BACKEND ONLY) ====
FROM eclipse-temurin:17-jdk AS builder

# Set working directory
WORKDIR /app

# Install necessary packages
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy only Java backend files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src/main src/main

# Make Maven wrapper executable
RUN chmod +x mvnw

# Download dependencies and build
RUN ./mvnw clean package -DskipTests

# Create runtime image
FROM eclipse-temurin:17-jre

# Set working directory
WORKDIR /app

# Copy only the built JAR
COPY --from=builder /app/target/tracker-0.0.1-SNAPSHOT.jar app.jar

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=digitalocean"]

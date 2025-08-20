# Backend Dockerfile
FROM eclipse-temurin:21-jdk as builder

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy pom.xml for dependency resolution
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build application
RUN mvn package -DskipTests -Dspring-javaformat.skip=true

# Create runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy JAR from build stage
COPY --from=builder /app/target/*.jar app.jar

# Create non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
RUN chown -R appuser:appgroup /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]

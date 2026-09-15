# ==============================================================================
# Stage 1: Build stage using official Eclipse Temurin JDK 26 and Maven Wrapper
# ==============================================================================
FROM eclipse-temurin:26-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and POM first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Ensure the Maven wrapper script has executable permissions
RUN chmod +x mvnw

# Download dependencies in an isolated layer
RUN ./mvnw dependency:go-offline -B

# Copy application source code
COPY src/ src/

# Build and package the Spring Boot executable JAR (skipping test execution)
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Stage 2: Runtime stage using lightweight Eclipse Temurin JRE 26
# ==============================================================================
FROM eclipse-temurin:26-jre

WORKDIR /app

# Create a dedicated non-root user and group for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built JAR artifact from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set file ownership to non-root user
RUN chown -R appuser:appuser /app

# Run container as non-root user
USER appuser

# Spring Boot default port
EXPOSE 8080

# Run Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]

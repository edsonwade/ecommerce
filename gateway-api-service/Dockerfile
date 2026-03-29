# ===================================================================
# Stage 1: Build Stage
# ===================================================================
FROM maven:3.9.5-eclipse-temurin-17 AS builder


# Set working directory
WORKDIR /build

# Copy entire project structure (required for multi-module Maven)
COPY . .

# Build only the gateway-api-service module
RUN mvn -B clean package -DskipTests=true -pl gateway-api-service -am

# ===================================================================
# Stage 2: Runtime Stage
# ===================================================================
FROM eclipse-temurin:17-jre-alpine

# Labels for metadata
LABEL maintainer="saas-team@company.com" \
    version="1.0.0" \
    description="SaaS Application - Production Ready"

# Set working directory
WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

# Create necessary directories
RUN mkdir -p /app/logs /app/config /app/temp && \
    chown -R appuser:appgroup /app

# Copy the built artifact from builder stage
COPY --from=builder /build/gateway-api-service/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM Options for production
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+HeapDumpOnOutOfMemoryError"

# Entrypoint with Java options
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ============================================
# Stage 1: Build
# ============================================
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy Gradle config first (layer caching)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle/ gradle/

# Download dependencies (cached if build files unchanged)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src/ src/

# Build application
RUN gradle bootJar --no-daemon

# ============================================
# Stage 2: Runtime (ARM64/AMD64 compatible)
# ============================================
FROM eclipse-temurin:17-jre-jammy

# Install gosu for privilege dropping
RUN apt-get update && apt-get install -y --no-install-recommends gosu \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Create upload directory
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

# Copy entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# Run as root initially, entrypoint drops to appuser
ENTRYPOINT ["/app/entrypoint.sh"]

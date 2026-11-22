# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

# Copy build descriptor and wrapper
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Copy source code and build production artifact
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for docker healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Run as dedicated non-root user and prepare persistent uploads directory
RUN useradd -u 1001 -m -s /bin/sh spring && \
    mkdir -p /app/uploads && \
    chown -R spring:spring /app

USER spring

COPY --from=builder --chown=spring:spring /workspace/target/*.jar /app/app.jar

EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

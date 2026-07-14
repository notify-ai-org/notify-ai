# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

ARG MAVEN_RETRY_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true -Dmaven.wagon.httpconnectionManager.ttlSeconds=120"

# Copy the parent pom and module poms to cache dependencies
COPY pom.xml .
COPY annotations/pom.xml annotations/
COPY api/pom.xml api/
COPY acp-server/pom.xml acp-server/
COPY client/pom.xml client/
COPY ollama4j/pom.xml ollama4j/
COPY adk-java/pom.xml adk-java/
COPY adk-java/core/pom.xml adk-java/core/
COPY adk-java/a2a/pom.xml adk-java/a2a/
COPY engine/pom.xml engine/
COPY access/pom.xml access/
COPY common/pom.xml common/
COPY examples/ecommerce-app/pom.xml examples/ecommerce-app/
COPY examples/banking-app/pom.xml examples/banking-app/

# Download dependencies offline to optimize build time. Maven Central can
# transiently time out in Docker builds, so retry cache warmup before failing.
RUN --mount=type=cache,target=/root/.m2 \
    for attempt in 1 2 3; do \
      mvn dependency:go-offline -B ${MAVEN_RETRY_OPTS} && exit 0; \
      echo "dependency:go-offline failed on attempt ${attempt}; retrying in 10s"; \
      sleep 10; \
    done; \
    mvn dependency:go-offline -B ${MAVEN_RETRY_OPTS}

# Copy the rest of the source code
COPY . .

# Build the project, skipping tests to speed up the process
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -T 1C ${MAVEN_RETRY_OPTS}

# Stage 2: Create the runtime image using GraalVM JDK
FROM ghcr.io/graalvm/jdk-community:17

WORKDIR /app

# Copy the built jar from the builder stage
# The spring-boot-maven-plugin repackages the jar to be executable
COPY --from=builder /app/access/target/vocabulary-agent-access-1.0.0.jar app.jar

# Expose the port the application runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

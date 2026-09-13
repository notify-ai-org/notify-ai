# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

ARG MAVEN_RETRY_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true -Dmaven.wagon.httpconnectionManager.ttlSeconds=120"

# Copy the parent pom and module poms to cache dependencies
COPY pom.xml .
COPY api/pom.xml api/
COPY acp-server/pom.xml acp-server/
COPY client/pom.xml client/
COPY adk-java/pom.xml adk-java/
COPY adk-java/core/pom.xml adk-java/core/
COPY adk-java/a2a/pom.xml adk-java/a2a/
COPY engine/pom.xml engine/
COPY artifact-engine/pom.xml artifact-engine/
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

# Stage 2: Ubuntu provides the system libraries supported by Playwright Chromium.
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy the built jar from the builder stage
# The spring-boot-maven-plugin repackages the jar to be executable
COPY --from=builder /app/access/target/vocabulary-agent-access-1.0.0.jar app.jar

# Install the exact browser revision required by the Playwright dependency in app.jar.
# The standalone CLI avoids starting Spring or connecting to application services.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN java -Dloader.main=com.microsoft.playwright.CLI -cp app.jar \
      org.springframework.boot.loader.launch.PropertiesLauncher install --with-deps chromium \
    && java -Dloader.main=com.microsoft.playwright.CLI -cp app.jar \
      org.springframework.boot.loader.launch.PropertiesLauncher screenshot \
      --browser chromium about:blank /tmp/playwright-smoke.png \
    && rm -f /tmp/playwright-smoke.png \
    && rm -rf /var/lib/apt/lists/*
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

# Expose the port the application runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

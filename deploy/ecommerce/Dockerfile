# E-commerce example app (storefront + API). Build from the repository root:
#   docker build -f deploy/ecommerce/Dockerfile --build-arg VITE_BASE_PATH=/ecommerce .

# Stage 1: build the React storefront with the path prefix it will be served under.
FROM node:20-alpine AS ui

WORKDIR /app/examples/ecommerce-app/ui

ARG VITE_BASE_PATH=/ecommerce
ENV VITE_BASE_PATH=${VITE_BASE_PATH}

COPY examples/ecommerce-app/ui/package.json examples/ecommerce-app/ui/package-lock.json ./
RUN npm ci

COPY examples/ecommerce-app/ui/ ./
# Writes to ../src/main/resources/static/portals/shop (see vite.config.ts).
RUN npm run build

# Stage 2: build the Spring Boot jar and the modules it depends on.
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

ARG MAVEN_RETRY_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true -Dmaven.wagon.httpconnectionManager.ttlSeconds=120"

COPY . .
COPY --from=ui /app/examples/ecommerce-app/src/main/resources/static/portals/shop/ \
    examples/ecommerce-app/src/main/resources/static/portals/shop/

# The client SDK is not a module of the root build, and the app uses SDK classes (InAppSubject)
# newer than the published artifact, so install it from the checked-out submodule first.
RUN --mount=type=cache,target=/root/.m2 \
    mvn install -DskipTests -f client/pom.xml ${MAVEN_RETRY_OPTS} \
    && mvn clean package -DskipTests -pl examples/ecommerce-app -am ${MAVEN_RETRY_OPTS}

# Stage 3: runtime.
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /app/examples/ecommerce-app/target/ecommerce-app-1.0.0.jar app.jar

# The H2 database file lives in ./data (mount a volume at /app/data to keep it).
EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]

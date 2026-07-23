<p align="center">
  <img src="assets/notify-ai-logo.svg" alt="Notify.ai" width="96" />
</p>

<h1 align="center">Notify.ai</h1>

<p align="center"><b>Agentic Notification & Event Orchestration System</b></p>

---

**Notify.ai** is an enterprise-grade, multi-module intelligent notification orchestration platform built on top of **Google ADK**. 

Unlike traditional notification services that simply dispatch static text, Notify.ai **understands your user's context**. Designed for seamless integration into existing businesses (e-commerce, banking, healthcare), it securely intercepts domain events from your upstream applications. Utilizing advanced LLM orchestration, it intelligently extracts underlying facts over time, builds a comprehensive memory graph of user behavior, dynamically generates highly contextual message templates, and reliably dispatches them across multiple out-bound channels (Email, SMS, Push, Webhooks) when the user is most receptive.


## 🚀 Running Locally

To stand up the complete development environment locally:

### 1. Build All Modules
From the root directory, compile and install all submodules:
```bash
mvn clean install
```

### 2. Stand up Backend Services
Ensure **PostgreSQL** and **Redis** are running locally. The bundled Compose
file can start both dependencies with the same service names and credentials
used by the application:

```bash
export DB_PASSWORD=change-me
export REDIS_PASSWORD=change-me
docker compose up -d postgres redis
docker compose ps
```

PostgreSQL is exposed on `127.0.0.1:5432` with database `notify_db` and user
`notification_user`. Redis is exposed on `127.0.0.1:6379` and requires the
password from `REDIS_PASSWORD`.

### 3. Run the Backend Server
Launch the Spring Boot backend (`access` module):
```bash
mvn spring-boot:run -pl access
```
The server will boot on `http://localhost:8080`.

### 4. Build and Run the UI Portals
To compile the UI assets and copy them to the Spring Boot resources directory:
```bash
./notify-ui/build-all.sh
```
Alternatively, start the hot-reloading Vite dev server:
```bash
cd notify-ui/dev
npm install
npm run dev
```

### 5. Run Example Applications
Test the end-to-end integration by running one of the sample apps:
```bash
# Start the E-Commerce sample (runs on port 8090)
mvn spring-boot:run -pl examples/ecommerce-app

# Start the Banking sample (runs on port 8091)
mvn spring-boot:run -pl examples/banking-app
```

## 🐳 Running Locally With Docker Compose

The root `docker-compose.yml` can be used to run the app locally with its
PostgreSQL and Redis dependencies.

- `postgres` - PostgreSQL 16 database.
- `redis` - Redis 7 with password authentication.
- `vocab-agent` - the Notify.ai application image built from this repository.

### Environment Variables

Compose reads variables from your shell or from a local `.env` file in the repo
root. At minimum, provide:

```bash
DB_PASSWORD=change-me
REDIS_PASSWORD=change-me
OPENAI_API_KEY=sk-change-me
```

Optional values include `GROQ_API_KEY`, `SERVER_PORT`, `MANAGEMENT_PORT`,
`ACP_CORS_ALLOWED_ORIGINS`, and embedding model settings.

Keep local env files out of git and do not commit secrets.

### Start Dependencies Only

```bash
export DB_PASSWORD=change-me
export REDIS_PASSWORD=change-me
docker compose up -d postgres redis
mvn spring-boot:run -pl access
```

### Start the App With Compose

```bash
export DB_PASSWORD=change-me
export REDIS_PASSWORD=change-me
export OPENAI_API_KEY=sk-change-me
docker compose up -d --build postgres redis vocab-agent
```

The application is configured inside Compose to listen on the container network.
Use the Maven workflow above if you want the backend directly on
`http://localhost:8080` while still using Docker for Postgres and Redis.

### Useful Compose Commands

```bash
# Show container status
docker compose ps

# Follow application logs
docker compose logs -f vocab-agent

# Open psql in the Compose Postgres container
docker compose exec postgres psql -U notification_user -d notify_db

# Run a one-off SQL statement
docker compose exec postgres \
  psql -U notification_user -d notify_db \
  -c "ALTER TABLE message_templates ALTER COLUMN template TYPE TEXT;"

# Stop containers but keep volumes
docker compose down
```

---

## 👥 Developer Contact & Contributing

For questions, issues, or support:
- **Lead Developer**: Rohan Naik ([rohan.naik07@github](https://github.com/rohan-naik07))
- **Email**: rohan.notify.admin1203@gmail.com

### Contributing

We welcome contributions! Please follow these guidelines:
1. **Fork** the repository and create your branch from `master`.
2. Ensure your changes compile and all tests pass.
3. Follow the project's coding standards and naming conventions.
4. Submit a **Pull Request** with a detailed description of your changes.

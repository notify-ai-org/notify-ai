Unlike traditional notification services that simply dispatch static text, Notify.ai understands user and event context. It captures annotated domain events from upstream applications, extracts useful facts over time, builds memory around user behavior, generates contextual message templates, and dispatches notifications across channels such as email, SMS, push, and webhooks.

##  Architecture & Project Structure

Notify.ai is organized around clear product boundaries:

- **Agent Control Plane**: Coordinates AI agents that process events, extract facts, plan schedules, and generate intelligent notification templates.
- **Notification Engine**: Executes delivery through outbound channels and records delivery attempts, retries, and failures.
- **Client SDK**: Embeds into source applications and captures annotated domain events.
- **Admin Portals**: Provide tenant, event, schedule, template, memory, rule, and delivery-observability workflows.

##  Running Locally

To stand up the development environment locally:

### 1. Build All Modules
From the root directory, compile and install all submodules:
```bash
mvn clean install
```

### 2. Stand up Backend Services
Ensure the required database and cache services are running locally. Keep credentials in local environment variables or ignored configuration files.

### 3. Run the Backend Server
Launch the Spring Boot backend (`access` module):
```bash
mvn spring-boot:run -pl access
```
The server starts on the configured application port.

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
mvn spring-boot:run -pl examples/ecommerce-app

mvn spring-boot:run -pl examples/banking-app
```

---

##  Developer Contact & Contributing

For questions, issues, or support:
- **GitHub**: [notify-ai-org/notify-ai](https://github.com/notify-ai-org/notify-ai)
- **Email**: dev-support@notify.ai

### Contributing

We welcome contributions! Please follow these guidelines:
1. **Fork** the repository and create your branch from `master`.
2. Ensure your changes compile and all tests pass.
3. Follow the project's coding standards and naming conventions.
4. Submit a **Pull Request** with a detailed description of your changes.

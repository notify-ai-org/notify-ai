Unlike traditional notification services that simply dispatch static text, Notify.ai **understands your user's context**. Designed for seamless integration into existing businesses (e-commerce, banking, healthcare), it securely intercepts domain events from your upstream applications. Utilizing advanced LLM orchestration, it intelligently extracts underlying facts over time, builds a comprehensive memory graph of user behavior, dynamically generates highly contextual message templates, and reliably dispatches them across multiple out-bound channels (Email, SMS, Push, Webhooks) when the user is most receptive.

##  Architecture & Project Structure

This repository is built as a multi-module Maven project to enforce clear separation of concerns:

- **[acp-server (Agent Control Plane)](file:///Users/rohannaik/Desktop/notify/acp-server/README.md)**: The "brain" of the operation. Orchestrates GenAI agents (using Google ADK) to process incoming events, extract actionable facts, generate targeted schedules, and formulate intelligent notification templates.
- **[engine](file:///Users/rohannaik/Desktop/notify/engine/README.md)**: The robust delivery mechanism. Handles the reliable execution of notification jobs, interacts with external channel providers, and manages failures via an advanced Dead Letter Queue (DLQ) mechanism.
- **[client](file:///Users/rohannaik/Desktop/notify/client/README.md)**: A lightweight Java SDK meant to be embedded directly into your source applications. It uses Aspect-Oriented Programming (AOP) to intercept method executions, securely packaging them as semantic events, and pushes them to the `acp-server`.
- **[notify-ui](file:///Users/rohannaik/Desktop/notify/notify-ui/README.md)**: The administration portals built as React microfrontends under Vite.

##  Running Locally

To stand up the complete development environment locally:

### 1. Build All Modules
From the root directory, compile and install all submodules:
```bash
mvn clean install
```

### 2. Stand up Backend Services
Ensure **MySQL** (port `3306`) and **Redis** (port `6379`) are running on localhost. Create a MySQL database named `notify`.

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

---

##  Developer Contact & Contributing

For questions, issues, or support:
- **Lead Developer**: Rohan Naik ([rohan.naik07@github](https://github.com/rohan-naik07))
- **Email**: dev-support@notify.ai

### Contributing

We welcome contributions! Please follow these guidelines:
1. **Fork** the repository and create your branch from `master`.
2. Ensure your changes compile and all tests pass.
3. Follow the project's coding standards and naming conventions.
4. Submit a **Pull Request** with a detailed description of your changes.

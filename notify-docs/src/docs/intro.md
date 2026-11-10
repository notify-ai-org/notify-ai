**Notify.ai** is an enterprise-grade, multi-module intelligent notification orchestration platform built on top of **Google ADK**. Unlike traditional notification services that simply dispatch static text, Notify.ai understands user and event context. It captures annotated domain events from upstream applications, extracts useful facts over time, builds memory around user behavior, generates contextual message templates, and dispatches notifications across channels such as email, SMS, push, and webhooks.

## Sign In and Generate a Client ID

After login, open the **Clients** portal and generate a client. Copy the
generated **Client ID**. Your Spring Boot application uses this ID when the
Notify.ai SDK registers its scanned events, vocabulary, rules, and callbacks.


##  Add the Client ID to Your  App
In the upstream application that embeds the Notify.ai client SDK,
add the generated Client ID as a property

The current SDK property name is `client-token`, but the value to paste here is
the **Client ID** generated in the portal.

Define your business events, subjects, and rules using client SDK as shown in the Developer SDK section.

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

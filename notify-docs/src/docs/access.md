The `access` module is the primary executable web application for **Notify.ai**. It encapsulates the Spring Boot application entrypoint (`VocabAgentApplication`), exposes external REST controllers (for both the client SDK and the administrative portals), and serves the compiled static front-end microfrontend assets.

## 🏗️ Structure & Key Capabilities

- **Spring Boot Entrypoint**: `VocabAgentApplication.java` boots the application context.
- **Controllers**:
  - `ClientController`: Ingests vocabulary metadata and domain events from the client SDK.
  - `PortalController` & `PortalAuthFilter`: Gatekeepers protecting and serving static microfrontends.
  - `AdminAuthController`: Coordinates authentication (including Google OAuth and custom logins).
  - `DeadLetterController`: Exposes actions to query and replay failed notifications in the DLQ.
  - `TemplateScheduleController`: Manages template variables and schedule criteria.
- **Static Portal Assests**: Serves UI portals (Home, Events, Memory, Settings, DLQ, etc.) compiled by `notify-ui` under `src/main/resources/static/portals/`.

## 🚀 Running Locally

The `access` module can be run locally as a Spring Boot application:

### Prerequisites
1. **MySQL**: Running on `localhost:3306` with a database named `notify`.
   - Update credentials in `src/main/resources/application-local.properties` if needed.
2. **Redis**: Running on `localhost:6379`.
3. **OpenAI API Key**: Set `openai.api.key` in your properties or environment.

### Execution
From the root directory of the project, run:
```bash
mvn spring-boot:run -pl access
```
Alternatively, open `VocabAgentApplication.java` in your IDE and run the main method. By default, the application runs on port **8080** under the `local` profile.
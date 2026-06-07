The `api` module defines the shared schema, data contracts, and JPA/Redis repositories for the **Notify.ai** ecosystem. It acts as the central data contract layer imported by both the **Agent Control Plane (ACP) Server** and the **Notification Engine**.

## 🏗️ Structure & Key Entities

- **Models / Entities**:
  - `Event`: Represents raw business/domain events ingested from clients.
  - `EventCapture`: Represents captured execution events with state and context.
  - `AgentSessionEntity`: Tracks active agent reasoning states and histories.
  - `FactEntity`: Encapsulates extracted semantic memory and facts about users.
  - `NotificationJob`: Represents a scheduled or outbound notification message.
  - `DeadLetterRecord`: Captures failed dispatch attempts for offline recovery and manual retries.
- **Repositories**: JPA and Spring Data Redis repositories for entities (e.g., `EventRepository`, `FactRepository`, `NotificationJobRepository`, `MemoryPageRepository`).
- **Interfaces**: Common abstractions for core systems, including:
  - `NotificationConnector`: Outbound channel dispatchers (Email, SMS, Webhooks).
  - `FactStore`: Semantic memory stores.
  - `EmbeddingProvider`: Vector extraction logic.

## 🚀 Local Compilation

This is a data and interfaces library, so it cannot be run as an independent application. To compile and package the module locally:

```bash
# Build the api module
mvn clean install -pl api
```
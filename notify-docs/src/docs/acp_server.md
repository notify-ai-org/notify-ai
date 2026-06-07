The **ACP Server** is the intelligent core of the Notify.ai ecosystem. It receives incoming events from registered client SDKs and leverages Large Language Models (LLMs) via the Google ADK to process them, extract user facts, and dynamically compile schedules and templates.

## 🏗️ Key Responsibilities

1. **Event Ingestion**: Exposes REST endpoints (`/api/event`, `/api/vocabulary`) through the `access` gateway to ingest events and vocabulary metadata from client apps.
2. **Agent Orchestration**: Maintains and executes a pool of active GenAI agents. The `AgentOrchestrator` queues and processes reasoning tasks (such as fact extraction, context tracking, and template construction).
3. **Fact Extraction**: Background workers read raw logs and event payloads, feeding them into specialized agents to construct a memory graph of user behavior.
4. **Schedule Generation**: Determines optimal delivery times for notification templates based on user context and vocabulary rules.

## 📦 Key Components

- `AgentOrchestrator`: Coordinates threads and resources for running GenAI agents. Supports asynchronous queued and direct synchronous execution.
- `EventConsumer`: Defines the reactive processing pipeline (RxJava) ensuring sequential task progression.
- `LogToMemoryAgentWorker`: A scheduled worker aggregating log buffers and sending them for memory consolidation.

## 🚀 Local Compilation

Since `acp-server` is packaged as a library module, it cannot be run as an independent application. It is bundled and executed within the **`access`** module web application.

To compile and package this module locally:
```bash
mvn clean install -pl acp-server
```

To run the application containing the ACP server, refer to the [access/README.md](file:///Users/rohannaik/Desktop/notify/access/README.md) instructions.
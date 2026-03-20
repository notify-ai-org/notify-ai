# Agent Control Plane (ACP) Server

The **ACP Server** is the intelligent core of the Notify ecosystem. It receives incoming events from registered clients and leverages Large Language Models (LLMs) via the Google ADK to process them. 

## Responsibilities

1. **Event Ingestion**: Exposes REST endpoints (`/api/event`, `/api/vocabulary`) to receive raw events and domain intelligence from the client SDK.
2. **Agent Orchestration**: Maintains a pool of active GenAI agents. When an event requires processing (e.g., extracting facts, determining user sentiment, or generating a contextual message template), the `AgentOrchestrator` securely dispatches the task to the appropriate agent.
3. **Fact Extraction**: Background workers routinely scour raw logs and event payloads, feeding them into specialized agents to build a historical graph of user interactions (Memory Assembly).
4. **Schedule Generation**: Determines *when* notification templates should be dispatched to the `engine` based on event context and rules.

## Key Components

- `AgentOrchestrator`: A robust thread and task manager that queues requests and assigns them to available GenAI agents. Supports both queued execution (`executeTaskWithAgent`) and immediate synchronous execution (`executeDirect`).
- `EventConsumer`: Defines the reactive pipeline (RxJava) ensuring sequential processing of tasks (e.g., templates MUST be generated before schedules are created).
- `LogToMemoryAgentWorker`: A scheduled background worker that aggregates logs and sends them to the `FactConsumer` to build memory graphs.

## Running the Server
```bash
mvn spring-boot:run -pl acp-server
```

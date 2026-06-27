The **ACP Server** (Agent Control Plane) is the intelligent core of the Notify.ai ecosystem. It receives incoming events from registered client SDKs, leverages Large Language Models (LLMs) via the **Google Agent Development Kit (ADK)**, extracts semantic user facts, and dynamically compiles notification schedules and message templates — all without manual configuration.

---

##  Key Responsibilities

### 1. Event Ingestion using REST
The ACP Server exposes two primary REST endpoints for event ingestion.

- **`POST /api/event`** — Receives a batch of `EventCapture` objects from the client SDK. Each capture contains the intercepted method's parameters, metadata, and contextual payload. Captures are immediately persisted and enqueued for asynchronous agent processing.
- **`POST /api/event/sync`** — Same as above but blocks until the entire processing pipeline completes. Useful for testing or lower-throughput integrations.
- **`POST /api/vocabulary`** — Accepts `ClassModel` descriptors that define the domain vocabulary (entity classes and their attributes). The server upserts these into the vocabulary graph, which agents later use to reason about user data semantics.
- **`POST /api/vocabulary/rules/process`** — Accepts a natural-language rule description and dispatches it to the `RuleProcessorAgent`, which converts it into an executable condition expression using vocabulary and persists it.

### 2. Event Ingestion using Kafka

For high-throughput production deployments, events are delivered to the ACP Server over Kafka rather than HTTP.Kafka delivery is wired into the same reactive processing pipeline used by the REST path.

**Topic & Partition Model**

All client SDK events are published to a single topic for free tier: `notify-v1-events`. On startup, the consumer probes Kafka for the number of active partitions and spawns **one consumer thread per partition**, ensuring full topic parallelism without manual configuration. But cross tenant data security isn't guaranteed.

For paid cloud hosted tiers, we have sharded acp-servers, each managing a section of topics which are configured per tenant. This ensures cross tenant data security and allows us to scale the ACP servers independently.

We have 12 partitions for each topic and a consumer group with 12 corresponding consumer threads. Each subject's event will be emitted in a fixed partition using a hash function, so that all events of a subject are processed in order, as Kafka guarantees ordering only per partition.

In future, we will configure per channel consumer groups to scale each channel traffic independently.

**Consumer Lifecycle**

Each consumer thread runs a tight `poll → process → commitSync` loop at a 100 ms polling interval. If a thread encounters a fatal error it is automatically **respawned** — the crashed consumer is removed from the pool, closed, and a fresh consumer is subscribed to the topic, maintaining thread pool capacity without operator intervention.

**Offset Management**

Offsets are committed **synchronously** after each successfully processed batch (`commitSync`). During a consumer group rebalance (`onPartitionsRevoked`), a synchronous commit is also performed to avoid re-processing records already handled before the rebalance. If Kafka reports an `OffsetOutOfRangeException`, the consumer seeks to the latest available offset and commits, preventing an infinite error loop.

### 3. Agent Orchestration
The `AgentOrchestrator` manages a **pool of GenAI agents** grouped by functional type (e.g., `EventProcessor`, `MessageTemplateAgent`, `EventSchedulerAgent`, `RuleProcessor`). Key behaviours:

- **Core pool** (`agent.orchestrator.core-pool-size`, default `10`): A protected floor of always-available agents that are never evicted.
- **Max pool** (`agent.orchestrator.max-pool-size`, default `20`): The burst ceiling. Overflow agents (those registered beyond the core pool) are eligible for idle eviction.
- **Idle eviction**: A scheduled cleanup task (interval: `agent.orchestrator.cleanup-interval-seconds`, default `60s`) evicts overflow agents that have been idle longer than `agent.orchestrator.idle-timeout-seconds` (default `300s`).
- **Task dispatch**: Tasks are built as RxJava `Flowable` streams. Sequential pipelines (e.g., generate template → then schedule) use `Flowable.concat`; parallel channel processing uses `Flowable.merge`.
- **Snapshot restoration**: On startup, the orchestrator queries persisted `AgentSnapshot` records to restore agents that were mid-task during the last shutdown, preventing data loss.
- **Dynamic configuration**: Pool size, idle timeouts, and cleanup intervals are all governed by `@ManagedConfiguration`-annotated fields, which can be updated at runtime via `POST /api/admin/config/apply` without a restart.


### 4. Fact Extraction & Memory
The `LogToMemoryAgentWorker` is a scheduled background service that runs independently of the real-time event pipeline:

- It periodically reads buffered agent logs from the `AgentLogRepository`.
- It feeds these logs to a memory-consolidation agent that extracts higher-order semantic facts (e.g., behavioural patterns, preference signals) and stores them in the `FactStore`.
- These consolidated facts are later retrieved by the `RetrievalPlanner` to enrich future event prompts, creating a **growing long-term memory** for each user/subject.

### 5. Vocabulary & Rule Processing
- **Vocabulary graph**: A hierarchical structure of `Vocabulary` entities. Class-level nodes represent domain entities (e.g., `Order`, `User`); attribute-level nodes represent their fields. This graph is consulted by agents when reasoning about what data is meaningful.
- **Rule processing**: Natural-language rule definitions submitted via `/api/vocabulary/rules/process` are transformed by the `RuleProcessorAgent` into structured condition expressions (e.g., `order.total > 500`) and stored in the `RuleRepository`. These are evaluated during the event processing pipeline to influence notification decisions.

---

##  Local Compilation

Since `acp-server` is packaged as a library module, it cannot be run as an independent application. It is bundled and executed within the **`access`** module web application.

To compile and package this module locally:
```bash
mvn clean install -pl acp-server
```

To run the full application (including the ACP server), refer to the [Access module](file:///Users/rohannaik/Desktop/notify/access/README.md) running instructions.
# Notification Engine

The **Notification Engine** acts as the reliable delivery mechanism for the Notify architecture. Once the `acp-server` has formulated what to send and when, it passes the `NotificationJob` down to this engine for execution.

## Responsibilities

1. **Job Dispatch**: Continuously polls or listens for active `NotificationJob` entities. 
2. **Multi-Channel Delivery**: Resolves the exact channels (Email, SMS, Push, etc.) required for the user and invokes the specific third-party integrations to send the message.
3. **Resilience & Retry**: Tracks delivery attempts in `NotificationAttemptLog`. 
4. **Dead Letter Queue (DLQ)**: Failed interactions or untriaged execution errors are safely captured in `DeadLetterRecord` entities. The `DeadLetterManager` provides methods to review and replay failed notifications.

## Key Components

- `DispatcherWorkerPool`: Initializes dedicated thread pools (`ExecutorType.DISPATCHER`) to parallelize the outbound delivery of notifications without blocking the central server threads.
- `NotificationWorker`: Processes individual `NotificationJob` workloads, handling success and failure states gracefully.
- `DeadLetterManagerImpl`: Extracts failed payload snapshots and maintains them for manual or automated replays.

## Running the Engine
```bash
mvn spring-boot:run -pl engine
```

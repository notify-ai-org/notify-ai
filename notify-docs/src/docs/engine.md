The **Notification Engine** acts as the reliable delivery layer for the **Notify.ai** architecture. Once the agent control plane has formulated what to send and scheduled it, the engine executes the resulting `NotificationJob` and dispatches it over the requested outbound channels.

## 🏗️ Key Responsibilities

1. **Job Dispatch**: Continuously polls or listens for active `NotificationJob` entities.
2. **Multi-Channel Delivery**: Resolves target channels (Email, SMS, Push, Webhooks) and invokes specific provider integrations (SMTP, Twilio, Webhook) to send messages.
3. **Resilience & Retry**: Logs attempts in `NotificationAttemptLog` and handles transient network errors.
4. **Dead Letter Queue (DLQ)**: Captures failed deliveries inside `DeadLetterRecord` database entries. Provides automated and manual retry endpoints.

## 📦 Key Components

- `DispatcherWorkerPool`: Creates thread pools (`ExecutorType.DISPATCHER`) to parallelize outbound delivery tasks.
- `NotificationWorker`: Processes individual job workloads and manages successes or failures.
- `DeadLetterManagerImpl`: Captures snapshots of failed jobs for later analysis and replaying.

## 🚀 Local Compilation

Since `engine` is built as a library module, it cannot be run independently. It is imported and runs inside the main executable **`access`** module.

To compile and package the engine module locally:
```bash
mvn clean install -pl engine
```

To run the application containing the Notification Engine, see the [access/README.md](file:///Users/rohannaik/Desktop/notify/access/README.md) execution instructions.
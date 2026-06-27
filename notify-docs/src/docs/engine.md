The **Notification Engine** acts as the reliable delivery layer for the **Notify.ai** architecture. Once the agent control plane has formulated what to send and scheduled it, the engine executes the resulting `NotificationJob` and dispatches it over the requested outbound channels.

##  Key Responsibilities

1. **Job Dispatch**: Continuously polls or listens for active `NotificationJob` entities.
2. **Multi-Channel Delivery**: Resolves target channels (Email, SMS, Push, Webhooks) and invokes specific provider integrations (SMTP, Twilio, Webhook) to send messages.
3. **Resilience & Retry**: Logs attempts in `NotificationAttemptLog` and handles transient network errors.
4. **Dead Letter Queue (DLQ)**: Captures failed deliveries inside `DeadLetterRecord` database entries. Provides automated and manual retry endpoints.

## Configurable Channel Connectors

The engine uses a **plugin-style connector model**: each outbound channel (Email, SMS, Webhook, etc.) is backed by an implementation of the `NotificationConnector` interface, and every aspect of that connector — which class to use, how many instances to run, and how aggressively to retry — is driven entirely by Spring properties. No code changes are needed to swap providers.

### Connector Interface & Base Class

All connectors implement `NotificationConnector` and extend `AbstractNotificationConnector`, which provides:

- **`bind(ChannelConfig)`** — Attaches the channel's configuration (retry delays, max attempts, backoff multiplier) to the connector instance at initialization time.
- **`init(AtomicReference<ConnectorMetrics>)`** — Wires up a shared metrics reference so each instance can record sent/retried/failed counters.
- **`retryWithBackoff(NotificationJob, Runnable)`** — A built-in retry loop that executes the delivery action up to `maxAttempts` times, sleeping `delay * backOffMultiplier^attempt` milliseconds between attempts. On final failure, it hands the job off to `DeadLetterManagerImpl` and re-throws the exception.

### Configuration Schema

Connectors are declared under the `connector.channel.<channelName>` prefix in `application.properties` or `application.yml`:

```properties
# Email channel — SMTP connector, 3 parallel instances
connector.channel.EMAIL.clazz=com.notify.agent.connectors.SmtpEmailConnector
connector.channel.EMAIL.instances=3
connector.channel.EMAIL.delay=1000
connector.channel.EMAIL.maxAttempts=3
connector.channel.EMAIL.backOffMultiplier=2

# SMS channel — Twilio, 2 parallel instances
connector.channel.SMS.clazz=com.notify.agent.connectors.TwilioSmsConnector
connector.channel.SMS.instances=2
connector.channel.SMS.delay=500
connector.channel.SMS.maxAttempts=2
connector.channel.SMS.backOffMultiplier=2

# Webhook channel
connector.channel.WEBHOOK.clazz=com.notify.agent.connectors.WebhookConnector
connector.channel.WEBHOOK.instances=1
connector.channel.WEBHOOK.delay=2000
connector.channel.WEBHOOK.maxAttempts=4
connector.channel.WEBHOOK.backOffMultiplier=3
```

### Built-in Connector Implementations

| Connector | Channel key | Provider |
|---|---|---|
| `SmtpEmailConnector` | `EMAIL` | Jakarta Mail (SMTP / SMTPS) |
| `TwilioSmsConnector` | `SMS` | Twilio REST API |
| `WebhookConnector` | `WEBHOOK` | Generic HTTP POST |

##  Local Compilation


Since `engine` is built as a library module, it cannot be run independently. It is imported and runs inside the main executable **`access`** module.

To compile and package the engine module locally:
```bash
mvn clean install -pl engine
```

To run the application containing the Notification Engine, see the [access/README.md](file:///Users/rohannaik/Desktop/notify/access/README.md) execution instructions.
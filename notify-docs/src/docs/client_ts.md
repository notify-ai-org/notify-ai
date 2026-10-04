The **Notify.ai TypeScript SDK** captures business events from Node.js services and sends them to the Notify.ai control plane. It speaks the same protocol as the Java, Go and Python SDKs, so services in any of these languages look the same to Notify.ai.

It needs Node 18 or later and has no runtime dependencies. Kafka support is an optional subpath built on `kafkajs`.

##  Installation

```bash
npm install @notify-ai/client
npm install kafkajs   # only for the Kafka transport
```

##  Quick Start

Sign in to the Notify.ai portal, open **Clients**, generate a client, and use its **Client ID** as `clientToken`.

```ts
import { NotifyClient, defineModel, field, EmailSubject } from "@notify-ai/client";

interface OrderPayload { orderId: string; customerId: string; amount: number }

// TypeScript types are erased at runtime, so the vocabulary is declared.
// Field keys are checked against OrderPayload.
const OrderModel = defineModel<OrderPayload>("OrderPayload", {
  description: "Payload for order placement events",
  fields: {
    orderId: field.string("Unique order identifier"),
    customerId: field.string("Customer who placed the order"),
    amount: field.double("Total order amount in USD"),
  },
});

const client = new NotifyClient({
  acpServerUrl: "http://localhost:8080",
  applicationName: "orders-service",
  clientToken: "client-your-generated-id",
});

class OrderService {
  @client.Event<OrderPayload>({
    key: "ORDER_PLACED", description: "Customer placed an order", eventType: "static",
    scheduleIntent: "immediate", preferredTimeWindow: "09:00-18:00", priority: 2, payload: OrderModel,
  })
  async placeOrder(p: OrderPayload) { /* ... */ }
}

// Or without decorators:
const placeOrder = client.event({ key: "ORDER_PLACED", priority: 2, payload: OrderModel }, (p: OrderPayload) => p);

client.subjectSupplier<OrderPayload>("ORDER_PLACED", async (p) => {
  const c = await customers.get(p.customerId);
  return [new EmailSubject(c.email, { attributes: { firstName: c.firstName } })];
});

client.rule<OrderPayload>({ name: "fraud-check", event: "ORDER_PLACED" }, (p) => p.amount < 1000);

client.start(); // non-blocking; captures are flushed before exit (or await client.close())
```

A complete port of the e-commerce example, using every hook type, is in [`examples/ecommerce.ts`](https://github.com/notify-ai-org/client-ts/blob/main/examples/ecommerce.ts).

##  Java Annotation Equivalents

| Java | TypeScript |
|---|---|
| `@EnableNotify` + `notify.ai.properties.*` | `new NotifyClient({...})` + `client.start()` (or `configFromEnv()`) |
| `@Model` / `@Vocabulary` | `defineModel<T>("Name", { description, fields: { key: field.string("…") } })` |
| `@Event` | `client.event(spec, fn)` or the `@client.Event(spec)` method decorator |
| `@Rule` | `client.rule({ name, event }, fn)` |
| `@SubjectSupplier` | `client.subjectSupplier(event, fn)` |
| `@VocabularySupplier` | `client.vocabularySupplier(event, (schedule) => payload)` |
| `@Callback(when = BEFORE/AFTER)` | `client.callback(event, BEFORE, fn)` |
| `EmailSubject`, `SmsSubject`, … | `new EmailSubject(...)`, `new SmsSubject(...)`, … (same JSON) |

##  Payloads

The payload is the event function's first argument. Without a `payload` model, an object payload is flattened by its own keys.

The `@client.Event` decorator works with standard TypeScript 5 decorators and with `experimentalDecorators`.

When a call site only needs to fire an event, `client.emitter(spec)` returns an `emit(payload)` function.

##  What a Wrapped Call Does

1. `BEFORE` callbacks run. Their errors are logged and ignored.
2. Your function runs. Errors are recorded and rethrown unchanged.
3. `AFTER` callbacks run.
4. The subject supplier and every rule for the event run, plus rules registered without an event. Each subject must have a non-blank address.
5. A capture is buffered with the flattened payload, the call stack, duration, result or error, subjects and rule results.

A background loop sends buffered captures to Notify.ai. Its timers never keep the process alive.

### Sync and async
A wrapped sync function stays sync, and a wrapped async function returns its promise. Hooks may be async in both cases, because the capture is built in the background. Async `BEFORE` callbacks are awaited before native `async` functions run.

##  Configuration

Pass the options to `new NotifyClient(...)`, or call `configFromEnv()` to read the environment variables.

| Option | Environment variable | Default |
|---|---|---|
| `acpServerUrl` | `NOTIFY_AI_ACP_SERVER_URL` | `https://app.notify-ai.dev` |
| `applicationName` | `NOTIFY_AI_APPLICATION_NAME` | `notify-client` |
| `clientToken` | `NOTIFY_AI_CLIENT_TOKEN` | — |
| `basePackage` | `NOTIFY_AI_BASE_PACKAGE` | — |
| `bufferBatchSize` | `NOTIFY_AI_BUFFER_BATCH_SIZE` | 100 |
| `flushIntervalMs` | `NOTIFY_AI_FLUSH_INTERVAL_MS` | 100 |
| `maxBufferSize` | `NOTIFY_AI_MAX_BUFFER_SIZE` | 10000 (negative = unbounded) |
| `kafkaEnabled` + `kafka` | `NOTIFY_AI_KAFKA_ENABLED` | false |
| `flushOnExit` | — | true (flushes on `beforeExit`) |
| `logger` | — | `console` |

##  Kafka Transport

```ts
import { kafkaFactory } from "@notify-ai/client/kafka";

const client = new NotifyClient({ ...config, kafkaEnabled: true, kafka: kafkaFactory({ brokers: ["broker:9092"] }) });
```

- Captures go to the `notify-v1-events` topic, one record per subject. A subject lands on the same partition from any SDK.
- An access token with `profile: basic` forces HTTP even when Kafka is enabled.
- An API key and secret returned at registration switch the connection to SSL + SASL PLAIN.
- Scheduled triggers from `notify-v1-scheduled-events` become `SCHEDULED` captures. The event's vocabulary supplier receives the trigger and returns the payload.
- Payloads with credential-like fields are rejected before publishing.
- Records are sent uncompressed, because `kafkajs` only bundles GZIP. Brokers accept both.

##  Differences from the Java SDK

The wire format and endpoints are identical. The runtime behaviour differs in these ways:

- Registration is retried with backoff instead of disabling the SDK.
- The buffer is capped, and the oldest capture is dropped first when it is full.
- Failed sends are requeued in order with backoff.
- Tokens are refreshed before expiry as well as on a 401 response.
- On Kafka, a capture with no subjects is sent as one tenant-keyed record.
- `close()` and process exit flush pending captures.

##  Local Development

```bash
npm install
npm run typecheck && npm test && npm run build
```

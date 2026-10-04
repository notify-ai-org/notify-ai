The **Notify.ai Go SDK** captures business events from Go services and sends them to the Notify.ai control plane. It speaks the same protocol as the Java, Python and TypeScript SDKs, so services in any of these languages look the same to Notify.ai.

Go has no annotations or AOP, so each Java annotation becomes a registration call. The SDK needs Go 1.22 or later.

##  Installation

```bash
go get github.com/notify-ai-org/client-go
```

##  Quick Start

Sign in to the Notify.ai portal, open **Clients**, generate a client, and use its **Client ID** as `ClientToken`.

```go
type OrderPayload struct {
    OrderID    string  `json:"orderId"    notify:"orderId"    notifyDesc:"Unique order identifier"`
    CustomerID string  `json:"customerId" notify:"customerId" notifyDesc:"Customer who placed the order"`
    Amount     float64 `json:"amount"     notify:"amount"     notifyDesc:"Total order amount in USD"`
}

func (OrderPayload) NotifyModelDescription() string { return "Payload for order placement events" }

client := notify.New(notify.Config{
    ACPServerURL:    "http://localhost:8080",
    ApplicationName: "orders-service",
    ClientToken:     "client-your-generated-id",
})

// Call placeOrder wherever you called svc.PlaceOrder.
placeOrder := notify.Event(client, notify.EventSpec{
    Key: "ORDER_PLACED", Description: "Customer placed an order",
    EventType: "static", ScheduleIntent: "immediate", PreferredTimeWindow: "09:00-18:00", Priority: 2,
}, svc.PlaceOrder) // func(context.Context, OrderPayload) (OrderPayload, error)

notify.SubjectSupplier(client, "ORDER_PLACED", "Order customer",
    func(ctx context.Context, p OrderPayload) ([]notify.Subject, error) {
        c := customers.Get(p.CustomerID)
        return []notify.Subject{
            notify.NewEmailSubject(c.Email, "", "", "", map[string]string{"firstName": c.FirstName}),
        }, nil
    })

notify.Rule(client, notify.RuleSpec{Name: "fraud-check", Event: "ORDER_PLACED"},
    func(ctx context.Context, p OrderPayload) (bool, error) { return p.Amount < 1000, nil })

if err := client.Start(); err != nil { log.Fatal(err) }
defer client.Close(context.Background()) // flushes buffered captures
```

A complete port of the e-commerce example, using every hook type, is in [`examples/ecommerce`](https://github.com/notify-ai-org/client-go/blob/main/examples/ecommerce/main.go).

##  Java Annotation Equivalents

| Java | Go |
|---|---|
| `@EnableNotify` + `notify.ai.properties.*` | `notify.New(cfg)` + `client.Start()` (or `notify.ConfigFromEnv()`) |
| `@Model` / `@Vocabulary` | struct tags `notify:"name" notifyDesc:"..."`, `notify.RegisterModel[T]`, optional `NotifyModelDescription()` |
| `@Event` | `notify.Event(client, spec, fn)`, which returns a wrapped function with the same signature |
| `@Rule` | `notify.Rule(client, notify.RuleSpec{...}, fn)` |
| `@SubjectSupplier` | `notify.SubjectSupplier(client, event, description, fn)` |
| `@VocabularySupplier` | `notify.VocabularySupplier(client, event, description, fn)` |
| `@Callback(when = BEFORE/AFTER)` | `notify.Callback(client, event, notify.Before/After, fn)` |
| `EmailSubject`, `SmsSubject`, … | `notify.NewEmailSubject(...)`, `notify.NewSmsSubject(...)`, … (same JSON) |

When a call site only needs to fire an event, `notify.Emitter[P](client, spec)` registers an event with no business logic and returns `func(ctx, P) error`.

##  What a Wrapped Call Does

1. `Before` callbacks run. Their errors and panics are logged and ignored.
2. Your function runs. Errors are returned unchanged; panics are recorded and re-raised.
3. `After` callbacks run.
4. The subject supplier and every rule for the event run, plus rules registered without an event. Each subject must have a non-blank address.
5. A capture is buffered with the flattened payload, the call stack, duration, result or error, subjects and rule results.

A background dispatcher sends buffered captures to Notify.ai.

##  Configuration

Pass a `notify.Config`, or call `notify.ConfigFromEnv()` to read the environment variables.

| Field | Environment variable | Default |
|---|---|---|
| `ACPServerURL` | `NOTIFY_AI_ACP_SERVER_URL` | `https://app.notify-ai.dev` |
| `ApplicationName` | `NOTIFY_AI_APPLICATION_NAME` | `notify-client` |
| `ClientToken` | `NOTIFY_AI_CLIENT_TOKEN` | — |
| `BasePackage` | `NOTIFY_AI_BASE_PACKAGE` | — |
| `BufferBatchSize` | `NOTIFY_AI_BUFFER_BATCH_SIZE` | 100 |
| `FlushInterval` | `NOTIFY_AI_FLUSH_INTERVAL_MS` | 100ms |
| `MaxBufferSize` | `NOTIFY_AI_MAX_BUFFER_SIZE` | 10000 (negative = unbounded) |
| `KafkaEnabled` | `NOTIFY_AI_KAFKA_ENABLED` | false |
| `HTTPClient`, `Logger` | — | `http.Client{}`, `slog.Default()` |

##  Kafka Transport

Kafka support is built into the `notify` package:

```go
client := notify.New(notify.Config{..., KafkaEnabled: true},
    notify.WithKafka(notify.KafkaTransportFactory(notify.KafkaConfig{Brokers: []string{"broker:9092"}})))
```

- Captures go to the `notify-v1-events` topic, one record per subject. A subject lands on the same partition from any SDK.
- An access token with `profile: basic` forces HTTP even when Kafka is enabled.
- An API key and secret returned at registration switch the connection to SASL_SSL / PLAIN.
- Scheduled triggers from `notify-v1-scheduled-events` become `SCHEDULED` captures whose payload comes from the event's `VocabularySupplier`. Inside it, `notify.ScheduleFromContext(ctx)` returns the trigger.
- Payloads with credential-like fields (`password`, `apiKey`, `*secret`, private keys) are rejected before publishing. The check is also exported as `notify.ValidateCredentialFree`.

##  Differences from the Java SDK

The wire format and endpoints are identical. These runtime behaviours differ on purpose:

| | Java | Go |
|---|---|---|
| Registration failure | Logs and leaves the SDK inactive | Retries with backoff (1s to 60s); captures buffer meanwhile |
| Buffer | Unbounded | Capped at `MaxBufferSize`; drops the oldest capture when full |
| Failed sends | Retried after 1s | Unsent records are requeued in order; backoff 1s to 30s |
| Shutdown | Pending records are lost | `Close(ctx)` flushes within the deadline |
| Token refresh | Only on a 401 response | Also before expiry |
| Kafka capture with no subjects | Not sent | Sent as one tenant-keyed record |
| Hook registration errors | Exception at startup | Panic at registration (blank key, priority outside 1–5, blank rule name) |

##  Local Development

```bash
go test -race ./...
```

The integration tests run against an in-process Kafka cluster, so no broker is needed.

The **Notify.ai Python SDK** captures business events from Python services and sends them to the Notify.ai control plane. It speaks the same protocol as the Java, Go and TypeScript SDKs, so services in any of these languages look the same to Notify.ai.

The core uses only the standard library and needs Python 3.9 or later. Kafka support is an optional extra.

##  Installation

```bash
pip install notify-ai-client            # HTTP transport
pip install 'notify-ai-client[kafka]'   # + Kafka transport (confluent-kafka)
```

##  Quick Start

Sign in to the Notify.ai portal, open **Clients**, generate a client, and use its **Client ID** as `client_token`.

```python
from dataclasses import dataclass
from typing import Annotated

from notify_ai import Config, EmailSubject, NotifyClient, Vocabulary, model

@model("Payload for order placement events")
@dataclass
class OrderPayload:
    order_id: Annotated[str, Vocabulary("orderId", "Unique order identifier")]
    customer_id: Annotated[str, Vocabulary("customerId", "Customer who placed the order")]
    amount: Annotated[float, Vocabulary("amount", "Total order amount in USD")]

client = NotifyClient(Config(
    acp_server_url="http://localhost:8080",
    application_name="orders-service",
    client_token="client-your-generated-id",
))

class OrderService:
    @client.event("ORDER_PLACED", description="Customer placed an order", event_type="static",
                  schedule_intent="immediate", preferred_time_window="09:00-18:00", priority=2)
    def place_order(self, payload: OrderPayload) -> OrderPayload:
        ...

@client.subject_supplier("ORDER_PLACED", "Order customer")
def order_subjects(p: OrderPayload):
    c = customers.get(p.customer_id)
    return [EmailSubject(c.email, attributes={"firstName": c.first_name})]

@client.rule("fraud-check", event="ORDER_PLACED")
def fraud_check(p: OrderPayload) -> bool:
    return p.amount < 1000

client.start()   # non-blocking; captures are flushed at exit (or call client.close())
```

A complete port of the e-commerce example, using every hook type, is in [`examples/ecommerce.py`](https://github.com/notify-ai-org/client-python/blob/main/examples/ecommerce.py).

##  Java Annotation Equivalents

Each Java annotation has a Python counterpart:

| Java | Python |
|---|---|
| `@EnableNotify` + `notify.ai.properties.*` | `NotifyClient(Config(...))` + `client.start()` (or `Config.from_env()`) |
| `@Model` / `@Vocabulary` | `@model("description")` + `Annotated[T, Vocabulary("name", "description")]` |
| `@Event` | `@client.event("KEY", priority=..., ...)` on a function or method (sync or `async`) |
| `@Rule` | `@client.rule("name", event="KEY")` |
| `@SubjectSupplier` | `@client.subject_supplier("KEY")` |
| `@VocabularySupplier` | `@client.vocabulary_supplier("KEY")` |
| `@Callback(when = BEFORE/AFTER)` | `@client.callback("KEY", BEFORE)` / `AFTER` |
| `EmailSubject`, `SmsSubject`, … | `EmailSubject(...)`, `SmsSubject(...)`, … (same JSON) |

##  Payloads

The payload is the first argument of the event function. For methods it is the first argument after `self` or `cls`. The payload's type annotation registers it as a model automatically.

A payload can be:

- **A dataclass, pydantic model or annotated class.** Its vocabulary fields become the payload. If no field is annotated with `Vocabulary`, every field is used.
- **A dict.** Its own keys become the payload.

When a call site only needs to fire an event, `client.emitter("KEY", priority=1)` returns an `emit(payload)` function.

##  What a Decorated Call Does

1. `BEFORE` callbacks run. Their errors are logged and ignored.
2. Your function runs. Exceptions are recorded and re-raised unchanged.
3. `AFTER` callbacks run.
4. The subject supplier and every rule for the event run, plus rules registered without an event. Each subject must have a non-blank address.
5. A capture is buffered with the flattened payload, the call stack, duration, result or exception, subjects and rule results.

A background thread sends buffered captures to Notify.ai, so this works the same in sync, threaded and asyncio applications.

### Async
`async def` events are wrapped as coroutines, and their hooks may also be `async`. A synchronous event cannot await an `async` hook; that hook's result is recorded as an error.

##  Configuration

Pass a `Config`, or call `Config.from_env()` to read the environment variables.

| `Config` field | Environment variable | Default |
|---|---|---|
| `acp_server_url` | `NOTIFY_AI_ACP_SERVER_URL` | `https://app.notify-ai.dev` |
| `application_name` | `NOTIFY_AI_APPLICATION_NAME` | `notify-client` |
| `client_token` | `NOTIFY_AI_CLIENT_TOKEN` | — |
| `base_package` | `NOTIFY_AI_BASE_PACKAGE` | — |
| `buffer_batch_size` | `NOTIFY_AI_BUFFER_BATCH_SIZE` | 100 |
| `flush_interval` (seconds) | `NOTIFY_AI_FLUSH_INTERVAL_MS` | 0.1 |
| `max_buffer_size` | `NOTIFY_AI_MAX_BUFFER_SIZE` | 10000 (negative = unbounded) |
| `kafka_enabled` | `NOTIFY_AI_KAFKA_ENABLED` | False |
| `flush_on_exit` | — | True (flushes in an `atexit` hook) |

Logs go to the `notify_ai` logger.

##  Kafka Transport

```python
from notify_ai.kafka import KafkaConfig, kafka_factory

client = NotifyClient(Config(..., kafka_enabled=True),
                      kafka=kafka_factory(KafkaConfig(brokers="broker:9092")))
```

- Captures go to the `notify-v1-events` topic, one record per subject. A subject lands on the same partition from any SDK.
- An access token with `profile: basic` forces HTTP even when Kafka is enabled.
- An API key and secret returned at registration switch the connection to SASL_SSL / PLAIN.
- Scheduled triggers from `notify-v1-scheduled-events` become `SCHEDULED` captures whose payload comes from the event's vocabulary supplier. Inside it, `current_schedule()` returns the trigger.
- Payloads with credential-like fields are rejected before publishing.

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
python -m venv .venv && .venv/bin/pip install -e '.[dev]'
.venv/bin/pytest
```

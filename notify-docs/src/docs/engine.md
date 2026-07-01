The **Notification Engine** is the delivery layer of Notify.ai. After the control plane decides what should be sent and when, the engine handles channel execution, provider integration, retry behavior, and delivery observability.

It is designed to separate notification intelligence from delivery mechanics. The control plane can focus on decisions, templates, schedules, and personalization while the engine focuses on reliable outbound communication.

---

## Key Responsibilities

### 1. Delivery Execution

The engine receives scheduled or ready-to-send notification work from the backend runtime and dispatches it through the requested channel.

Supported channel patterns include:

- Email
- SMS
- Push notifications
- Webhooks
- Custom provider integrations

Each channel can be connected to a provider-specific implementation without changing the upstream application that emitted the original event.

### 2. Provider Connectors

Notify.ai uses a connector model for outbound delivery. A connector adapts Notify.ai's internal notification request into the provider-specific API or protocol required by a delivery service.

Examples include:

- SMTP or transactional email providers for email
- SMS providers for text messages
- Push providers for mobile or web notifications
- HTTP endpoints for webhook delivery

This keeps provider-specific concerns isolated from event processing and agent orchestration.

### 3. Retry And Resilience

Delivery can fail for temporary reasons such as network errors, provider throttling, transient service outages, invalid recipient state, or downstream timeouts.

The engine records delivery attempts and applies retry behavior where appropriate. Permanent failures are separated from transient failures so operators can inspect, retry, or resolve them without losing visibility into what happened.

### 4. Dead-Letter Handling

When a notification cannot be delivered after the configured retry policy, it is moved into a failure-handling path instead of being silently dropped.

This gives operators a place to inspect failed deliveries, understand why they failed, and decide whether they should be retried, ignored, or corrected through configuration or data changes.

### 5. Delivery Observability

The engine tracks the lifecycle of outbound notification attempts. This supports operational dashboards, audit views, and future delivery analytics.

Typical lifecycle signals include:

- queued
- sent to provider
- delivered when the provider supports delivery receipts
- failed
- retried
- dead-lettered
- opened or clicked when the channel supports engagement tracking

Delivery and read confirmation vary by channel. Email opens and SMS engagement are best treated as signals, while push and in-app events can provide stronger client-reported interaction data when the application reports those events back to Notify.ai.

### 6. Channel Extensibility

The engine is built so additional channels and providers can be added without redesigning the notification pipeline.

A new connector can be introduced for a provider while keeping the same upstream flow:

1. The application emits a domain event.
2. The control plane decides whether and how to notify.
3. The engine delivers through the selected channel.
4. Delivery status is recorded for operators and analytics.

---

## Deployment Model

The Notification Engine runs as part of the Notify.ai backend runtime. It is not typically operated as a standalone public service.

For production deployments, keep provider credentials and delivery configuration outside source control, restrict administrative access, and expose only the intended public application routes through a reverse proxy or load balancer.

Use the local development guide for startup and operational setup.

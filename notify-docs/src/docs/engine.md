> Channel credentials are now tenant-owned and stored in AWS Secrets Manager. Provision channels through `/api/v1/channels`, then submit credentials to `/api/v1/channels/{channelId}/credentials`. The repository guide `engine/README-secrets.md` covers migration, IAM, API usage and recovery.

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
- Application inbox endpoints for IN_APP delivery
- Meta WhatsApp Cloud API for WHATSAPP text and approved templates
- Firebase Cloud Messaging HTTP v1 for PUSH delivery

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

## IN_APP through the webhook connector

Both `connector.channel.IN_APP` and `connector.channel.WEBHOOK` use
`WebhookConnector`. There is no separate in-app connector or in-app environment
configuration. The receiving application implements its inbox and display logic.

Supply the application's delivery URL on the IN_APP subject, with an optional
user ID when multiple users share the endpoint:

```json
{"channel":"IN_APP","url":"https://app.example/inbox","userId":"customer-42","correlationId":"order-123"}
```

The Java SDK exposes
`new InAppSubject(url, userId, correlationId, attributes)` and a three-argument
`new InAppSubject(url, correlationId, attributes)` when the URL identifies the
recipient. `getAddress()` returns the URL. Subjects containing only a `userId`
need to be updated to supply `url`.

The endpoint receives the existing webhook envelope, with IN_APP recipient
context:

```json
{
  "id": "notification-123",
  "timestamp": "2026-09-15T09:00:00Z",
  "payload": "Your order is confirmed.",
  "channel": "IN_APP",
  "tenantId": "tenant-1",
  "recipientId": "customer-42"
}
```

`payload` is the rendered job template. `recipientId` is null when `userId` is
omitted. Standard WEBHOOK deliveries retain their existing envelope.
The header `X-Notification-Channel` is `in_app` for IN_APP deliveries.
Use non-sensitive job attributes `method` (default POST) and `expectedStatus`
(default any 2xx). Configure `signingSecret` and optional `authorization` through
the credential API; never put them in job attributes. Signing covers the raw
UTF-8 request body. Network and unsuccessful HTTP responses propagate through
the existing delivery failure flow.

Applications can deduplicate by `(tenantId, id, recipientId)` or by `id` for a
recipient-specific URL. There is no separate IN_APP idempotency header.

Send engagement callbacks to `POST /api/callbacks/notifications/{tenantId}/{channelId}/webhook`:

```json
{
  "eventId": "engagement-123",
  "notificationId": "notification-123",
  "recipientId": "customer-42",
  "channel": "IN_APP",
  "status": "SEEN",
  "occurredAt": "2026-09-15T09:00:00Z"
}
```

Sign callback bytes with the channel credential
`signingSecret`, using
`X-Notification-Signature: sha256=<hex>`. The callback provider is `webhook`,
and the supplied `IN_APP` channel is preserved as `in_app`. Omitting `channel`
keeps the existing webhook default. The old `/in_app` callback route and
`IN_APP_*` environment settings are no longer used. Keep callback secrets in
your application backend.

## WHATSAPP connector (Meta)

The connector is registered as `connector.channel.WHATSAPP` and calls
`https://graph.facebook.com/{version}/{phone-number-id}/messages`.

Create a WHATSAPP channel with provider `META_WHATSAPP`. Its non-sensitive
settings are `phoneNumberId` and `apiVersion`. Submit `accessToken` through the
credential API, with `appSecret` and `verifyToken` when callbacks are needed.
`WHATSAPP_ENABLED=false` disables sending globally.

Supply a subject with an international phone number:

```json
{"channel":"WHATSAPP","phoneNumber":"+14155552671"}
```

The Java SDK exposes `new WhatsAppSubject(phoneNumber, correlationId, attributes)`.
For text messages, the connector sends the rendered job template. Set
`whatsapp.previewUrl=true` in job attributes to enable link previews.

For an approved Meta template, set these job attributes:

```json
{
  "whatsapp.templateName": "order_confirmation",
  "whatsapp.languageCode": "en_US",
  "whatsapp.components": "[{\"type\":\"body\",\"parameters\":[{\"type\":\"text\",\"text\":\"ORD-123\"}]}]"
}
```

`whatsapp.components` is optional and must be a JSON array encoded as a string.
Its values must already be resolved. The connector does not create or approve
templates. Use approved templates when messaging outside Meta's customer service
window; text delivery remains subject to Meta's messaging rules.

Register your public HTTPS callback URL as
`/api/callbacks/notifications/{tenantId}/{channelId}/whatsapp` in Meta and subscribe the app to the
WhatsApp Business Account's messages events. The GET endpoint echoes
`hub.challenge` only when `hub.mode=subscribe` and `hub.verify_token` matches.
POST callbacks are verified against the raw request with the Meta app secret and
`X-Hub-Signature-256`.

Outbound messages carry the job ID in `biz_opaque_callback_data`. Signed status
callbacks for the configured sender map `sent → SENT`, `delivered → DELIVERED`,
`read → SEEN`, and `failed → FAILED`. Inbound messages and statuses without
Notify correlation data are acknowledged without recording a delivery event.
An HTTP success means Meta accepted the message; delivery is confirmed by
callbacks. Meta sends can still be duplicated after an ambiguous network failure;
the correlation field is not a provider idempotency guarantee.

See [Meta's Cloud API examples](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api)
and [Meta's webhook verification reference](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/webhooks/start/).

## PUSH connector (Firebase Cloud Messaging)

`connector.channel.PUSH` uses `FcmPushConnector`. It sends to the FCM
registration token in the existing `PushSubject`, including Android, iOS, and
web registrations. An Apple APNs token is not an FCM registration token; the
client application must integrate Firebase and register for messaging.

Create a PUSH channel with provider `FCM` and a non-sensitive `projectId`
setting. Submit `serviceAccountJson` and optional `callbackSecret` through the
credential API. The SecretResolver obtains a messaging-scoped access token and
passes it to the connector. No credentials file mount or Google ADC is used.
Enable the Firebase Cloud Messaging API and grant the service account permission
to send to that project. `PUSH_ENABLED=false` disables sending globally.

Recipient JSON:

```json
{"channel":"PUSH","deviceToken":"fcm-registration-token","correlationId":"order-123"}
```

The Java client already exposes
`new PushSubject(deviceToken, correlationId, attributes)`.
The rendered job template becomes the notification body. Job attributes:

```json
{
  "push.title": "Order update",
  "push.data.orderId": "ORD-123",
  "push.data.screen": "orders",
  "push.android": "{\"priority\":\"HIGH\"}",
  "push.apns": "{\"payload\":{\"aps\":{\"sound\":\"default\"}}}",
  "push.webpush": "{\"fcm_options\":{\"link\":\"https://app.example/orders\"}}"
}
```

All attributes above are optional. `push.imageUrl` adds a notification image.
Only `push.data.*` attributes become custom string data; other job attributes
are not forwarded. The connector adds `notificationId` and `correlationId`
to message data. Those keys and FCM-reserved keys cannot be overridden.
Platform options must be JSON objects encoded as strings. Use `push.data.*`
instead of platform-level `data` to preserve correlation. Platform options
must satisfy FCM's platform-specific requirements.

A successful send means FCM accepted the message, not that a device displayed
or opened it. HTTP failures propagate to the existing engine retry flow.
FCM does not guarantee idempotency for these sends; applications can use
`notificationId` to deduplicate processing after retries.

For delivery or engagement reporting, have your authenticated application
backend POST to `/api/callbacks/notifications/{tenantId}/{channelId}/push` using the same JSON
envelope as the webhook engagement example (omit its `channel` field). Set
`recipientId` to the FCM registration token and use
a status such as `DISPLAYED` or `OPENED`. Sign the raw request bytes using
the channel's `callbackSecret` in `X-Notification-Signature: sha256=<hex>`.
This is a Notify backend callback contract, not an FCM delivery webhook.
Never place the shared callback secret in mobile or browser code.

See [FCM HTTP v1 authentication](https://firebase.google.com/docs/cloud-messaging/send/v1-api)
and the [FCM message schema](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages).

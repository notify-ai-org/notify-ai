# Tenant channel configuration

Apply `migrations/003_unified_channel_config.sql` after `002_channel_secrets.sql`
before deploying against an existing database. The migration adds connector pool
and retry fields to `tenant_channel_config` and selects the connector class from
the existing provider. It does not copy environment values or credentials.

`TenantChannelConfig` replaces both the old `ChannelConfig` interface and
`ConnectorProperties.ChannelConfigImpl`. Connector pools are keyed by tenant and
channel ID. Changes to the persisted version, settings, secret reference, or
runtime fields refresh that channel's pool on its next use.

Create channels using `POST /api/v1/channels`. The request accepts `type`,
`provider`, `settings`, and optional `instances` (default 1), `delay` in
milliseconds (default 0), `maxAttempts` (default 1), and `backOffMultiplier`
(default 2). The provider determines the connector class. Channels start disabled;
successful credential provisioning activates them. Disabled channels cannot send
or process managed callbacks.

All connector settings come from the channel's `settings_json`. These keys are
accepted:

| Provider | Settings |
| --- | --- |
| FCM | `projectId` |
| META_WHATSAPP | `phoneNumberId`, `apiVersion` |
| SMTP | `host`, `port`, `from`, `fromName`, `auth`, `startTls`, `ssl`, `connectionTimeoutMs`, `timeoutMs`, `writeTimeoutMs`, `defaultHtml`, `subjectPrefix`, `callbackAllowedIps` |
| WEBHOOK | `endpoint`, `callbackAllowedIps` |
| TWILIO | `fromNumber`, `callbackUrl`, `callbackAllowedIps` |

SMTP defaults are port 587, authentication and STARTTLS enabled, SSL disabled,
10,000 ms for each timeout, HTML content enabled, and an empty subject prefix.
Boolean settings use `true` or `false`; timeouts must be positive. SMTP requires
STARTTLS or SSL. Callback IP restrictions support comma-separated IPs or CIDRs.
The registry supplies `enabled` from the channel record, not from environment
properties or the settings JSON.

Store credentials through `/{channelId}/credentials`. SecretResolver supplies
the credential map separately: FCM exchanges its service account for an access
token; WhatsApp uses `accessToken`, `appSecret`, and `verifyToken`; SMTP uses
`username`, `password`, and optional `callbackSecret`; webhook uses
`signingSecret` and optional `authorization`; Twilio uses `accountSid` and
`authToken`. Secrets are not stored in the registry or connector pool.

Use tenant-bound callback URLs:
`/api/callbacks/notifications/{tenant}/{channelId}/{provider}`. Twilio's
`callbackUrl` is used for both outgoing status callbacks and signature
verification; notification payloads cannot override it. WhatsApp subscription
verification uses GET on the tenant-bound `whatsapp` callback URL.

The previous `connector.channel.*`, `notification.push.*`,
`notification.whatsapp.*`, `notification.smtp.*`, and `twilio.*` connector
properties no longer configure delivery. Populate each tenant channel's settings
and provision its credentials before sending jobs through it.

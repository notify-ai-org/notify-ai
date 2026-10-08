<p align="center">
  <img src="../assets/notify-ai-logo.svg" alt="Notify.ai" width="96" />
</p>

<h1 align="center">Notify.ai Deployment</h1>

This deployment runs the application, nginx, PostgreSQL, and Redis as one
Compose stack.

## First-time setup

```bash
cd /opt/vocab-agent
cp deploy/ec2.env.example deploy/ec2.env
nano deploy/ec2.env
```

Set strong values for `DB_PASSWORD`, `REDIS_PASSWORD`, and `OPENAI_API_KEY`.
Set `GROQ_API_KEY` for agents configured with `groq/*` models.
Keep `deploy/ec2.env` on the server only; it is ignored by git.

If nginx is already installed on the EC2 host, stop it before starting the
Compose nginx container:

```bash
sudo systemctl disable --now nginx
```

The nginx container expects Let's Encrypt certificates at:

```text
/etc/letsencrypt/live/app.notify-ai.dev/fullchain.pem
/etc/letsencrypt/live/app.notify-ai.dev/privkey.pem
```

## Start or redeploy

```bash
docker compose --env-file deploy/ec2.env up -d --build
```

## Apply database migrations

Run the bundled SQL migration after schema changes or when an existing EC2
database reports missing columns/wrong column types:

```bash
docker compose --env-file deploy/ec2.env exec -T postgres psql -U notification_user -d notify_db -f - < deploy/migrations/001_notify_postgresql.sql
```

For tenant-managed connectors, apply migrations `002_channel_secrets.sql`,
`003_unified_channel_config.sql`, `004_secret_metadata_version_id.sql`, and
`005_unique_tenant_channel_type.sql` in order.
Apply 004 before starting the updated application; it preserves credential version
IDs while renaming their column and removing the stored secret ARN. Migration
005 enforces one channel per tenant/type, including disabled channels; existing
duplicates must be reviewed and resolved before it can succeed. See [tenant channel configuration](channel-configuration.md)
for the settings and credential migration from environment properties.

For tenant prompt versions, apply `006_tenant_prompt_store.sql` before deploying
the updated application. It creates the version catalog and active selections;
prompt content uses the existing artifact engine/S3 configuration. See
[tenant prompts](tenant-prompts.md) for the API and activation workflow.

For structured facts, apply `007_fact_references.sql` before deploying the updated
application. It adds `event`, `entityRef`, and `subject` storage, copies legacy
`fact_user` values into the subject field, and normalizes known fact types to
`event`, `notification`, `subject`, or `entity`. Missing/unknown legacy types must
be classified before the migration can succeed; the transaction rolls back on
those rows. Entity references remain null when no explicit reference exists.
Existing tenant overrides of the log-to-facts prompt should adopt the updated
bundled output contract; known legacy outcome types and `user` remain accepted
for compatibility.

## Check status

```bash
docker compose ps
docker compose logs -f vocab-agent
docker compose logs -f nginx
curl -I https://app.notify-ai.dev/actuator/health
```

## Restart after EC2 reboot

Docker containers use `restart: unless-stopped`, so they should come back after
Docker starts. If needed, run:

```bash
cd /opt/vocab-agent
docker compose --env-file deploy/ec2.env up -d
```

Adaptive event scheduling: apply `migrations/008_adaptive_event_scheduling.sql` before deploying the new processor/scheduler contracts. It adds subject history snapshots, schedule event/subject references, revisions, revocation and pending-mutation state. Existing shared schedules retain revision zero and continue running. Update tenant prompt overrides and restart agents; standby context is stored in the database (see migration 014). See `acp-server/README.md` for the output contracts and recovery behavior.

Recipient preferences: apply `migrations/010_recipient_preferences.sql` before deploying the preferences portal. It adds per-recipient opt-outs, preferred channel and quiet hours keyed by the stable subject ID, and a `recipient_id` column on notification jobs. The engine records suppressed sends as `SUPPRESSED` attempt logs (excluded from channel metrics); jobs created before the migration carry no recipient and are not filtered. Rebuild portals with `./notify-ui/build-all.sh` so `/portals/preferences` is served.

Multi-language templates: apply `migrations/011_template_languages.sql`. It adds a `language` (BCP 47 tag) to recipient preferences and to `message_templates`. When a recipient has a language, the template agent writes that event's copy in it and the template is stored as a separate per-language variant; existing templates remain the default copy. Tenant prompt overrides keep working because the language instruction is also sent in the request. Domain content values (brand copy such as `${PRIMARY_CTA_LABEL}`) are not translated.

DLQ replay thread and agent stop: apply `migrations/012_dlq_replay_and_agent_stop.sql`. Replays are claimed (`REPLAYING`) and dispatched on the engine's `dlq-replay` thread after `dlq.replay-delay-ms` (default 2000); a failed dispatch returns the record to `PENDING` with `replay_error`, and claims older than 5 minutes are released. The migration drops the Hibernate enum check constraint that would reject `REPLAYING`. Stopping an agent task from the agents portal works on the instance running it; with `REDIS_ENABLED=true` the request is broadcast on `notify:agent-task-stop` so any replica can receive it.

Compact-named schedule tables: apply `migrations/013_event_schedule_compact_names.sql` on databases whose schedule table is `eventschedule` rather than `event_schedule`. Migration 008 only covers the snake_case names, and Hibernate's `ddl-auto` cannot add the NOT NULL `mutationpending`, `revoked`, `revision` and `version` columns to a populated table, so schedule reads and updates fail at runtime. The migration adds them with 008's defaults and the matching `(tenant_id, subject, revoked)` index. It is idempotent and does nothing on snake_case databases.

Redis-backed scheduler: with `REDIS_ENABLED=true` the engine's Quartz scheduler stores jobs and triggers in Redis (`engine/.../quartz/RedisJobStore`, keys under `notify:quartz:`), so schedules survive restarts and each trigger fires on exactly one replica. With Redis disabled Quartz keeps its in-memory store and schedules are lost on restart, as before. Optional settings: `scheduler.job-store.key-prefix` (default `notify:quartz:`) and `scheduler.job-store.misfire-threshold-ms` (default 60000). The old `notify:scheduler:events` sorted set is no longer written or read; remove it once with `redis-cli DEL notify:scheduler:events`. Schedules that exist only in a running instance's memory at the time of this deploy are not migrated; they are lost when that instance stops, as with any restart before this change.

Postgres image with pgvector: `docker-compose.yml` runs `pgvector/pgvector:pg16` instead of `postgres:16-alpine`, because artifact search needs the `vector` extension library (without it every search fails with `could not access file "$libdir/vector"`). Same major version, so the existing `postgres-data` volume is reused. The old image is Alpine (musl) and the new one is Debian (glibc), which sort text differently, so rebuild indexes once after the first start on an existing volume:

```bash
docker compose --env-file deploy/ec2.env pull postgres
docker compose --env-file deploy/ec2.env up -d postgres
docker compose --env-file deploy/ec2.env exec postgres psql -U notification_user -d notify_db \
  -c 'CREATE EXTENSION IF NOT EXISTS vector;' -c 'REINDEX DATABASE notify_db;'
```

Take a `pg_dump` before switching. On a fresh volume no extra step is needed.

Standby events in the database: apply `migrations/014_standby_events.sql`. Standby snapshots move from Redis (`notify:standby:*`) to the `standby_event` table, so putting an event on standby no longer needs Redis. Snapshots held in Redis at deploy time are not migrated; they only provide context for later events and expire on their own. Remove the old keys with `redis-cli --scan --pattern 'notify:standby:*' | xargs redis-cli DEL` once deployed.

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

Adaptive event scheduling: apply `migrations/008_adaptive_event_scheduling.sql` before deploying the new processor/scheduler contracts. It adds subject history snapshots, schedule event/subject references, revisions, revocation and pending-mutation state. Existing shared schedules retain revision zero and continue running. Update tenant prompt overrides and restart agents; configure Redis for standby context. See `acp-server/README.md` for the output contracts and recovery behavior.

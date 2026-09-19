# Tenant prompt versions

`PromptStore` defines creation, listing, retrieval, activation, active-prompt lookup,
and deactivation. `DefaultPromptStore` uses the existing artifact engine for all
prompt content writes and reads. With the application's `S3ObjectStore`, content
is stored in the configured artifact S3 bucket using its existing encryption and
tenant storage layout. No separate S3 client or bucket is needed.

## Deploy

Apply `deploy/migrations/006_tenant_prompt_store.sql` to the application database
before deploying this change. The catalog uses PostgreSQL; Hibernate does not
create these tables. For the Compose deployment:

```bash
docker compose --env-file deploy/ec2.env exec -T postgres psql -U notification_user -d notify_db -f - < deploy/migrations/006_tenant_prompt_store.sql
```

Enable the existing artifact engine with `artifact.jpa.enabled=true`, configure
its `artifact.s3.*` properties, and run its STORE workflow workers. The prompt
store bean is registered with that feature and can be replaced by a custom
`PromptStore` bean. If the artifact engine is disabled, agents retain their
bundled prompts and prompt-management requests return 503.

## Tenant API

Send the application's normal bearer token. The tenant and principal are derived
from the authenticated context; request bodies cannot select another tenant.
Reads require `prompt.read`, `prompt.write`, or `admin`; changes require
`prompt.write` or `admin`. Use the functional agent ID, for example `RuleProcessor`.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/prompts/{agentId}/versions` | Create an immutable version from `{"content":"Your prompt text"}` |
| GET | `/api/prompts/{agentId}/versions?limit=100` | List newest versions (limit 1–1000) |
| GET | `/api/prompts/{agentId}/versions/{versionId}` | Read version metadata and prompt text |
| PUT | `/api/prompts/{agentId}/active/{versionId}` | Select an existing version, including an older version for rollback |
| GET | `/api/prompts/{agentId}/active` | Read the selected version and text (404 if none) |
| DELETE | `/api/prompts/{agentId}/active` | Clear the override; preserve version history |

Creation returns a UUID `versionId` and `artifactId`. It accepts up to 64,000
characters and does **not** activate the prompt. Artifact ingestion is asynchronous:
reading or activating a version returns 409 until its content is durably stored.
Check the artifact's storage status through the artifact API and retry after it
reaches `STORED`. Indexing does not have to finish to activate a prompt. A failed
activation preserves the previous selection.

## Agent behavior and consistency

`AgentRegistry` supplies a dynamic ADK instruction provider. It uses the trusted
tenant ID installed in the session by `AgentOrchestrator` and the session's user
ID to resolve the active prompt for that functional agent. It does not cache a
tenant's prompt in the shared agent object. Each instruction resolution reads the
current selection, so a selection change takes effect without restarting or
registering agents again, including on subsequent LLM calls of a running task.

When no override exists, the registry uses the existing bundled `prompt.md`.
Agent feedback instructions and tool retrieval instructions are appended to both
bundled and tenant prompts. The agent's existing `bypassStateInjection` setting
still controls placeholder substitution. Storage errors for an active override
fail the invocation instead of silently reverting to the bundled prompt.

The database stores version identifiers, artifact references, creator/time, and
one active pointer per `(tenant_id, agent_id)`; it stores no prompt text. A
composite foreign key prevents selecting a different tenant's or agent's version.
Concurrent activation is atomic and last-write-wins. Immutable JSON envelopes in
S3 include tenant, agent, and version identity so artifact content deduplication
cannot collapse two identical prompt versions.

Artifact intake and catalog insertion are separate operations. If intake succeeds
but catalog insertion fails, an unreferenced artifact can remain; it is never
automatically activated. Treat prompt artifacts as durable dependencies: deleting
one through generic artifact management makes that version unavailable. Prompt
versions follow the artifact engine's normal indexing and retention behavior.

## Verification

The store tests use a disposable PostgreSQL schema and a mocked artifact engine;
they do not call AWS. Set `NOTIFY_TEST_POSTGRES_URL` to a local test database, with
optional `NOTIFY_TEST_POSTGRES_USER` and `NOTIFY_TEST_POSTGRES_PASSWORD` (defaults:
`notify_test` / `notify-test-only`), then run:

```bash
mvn -pl access -am -Dfmt.skip=true -Dtest=DefaultPromptStoreTest,AgentRegistryPromptTest,PromptControllerTest,AgentToolExecutionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

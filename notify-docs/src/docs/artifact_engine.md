# Artifact Storage and Retrieval Engine

The **Artifact Storage and Retrieval Engine** gives applications a secure, tenant-aware
way to accept documents, retain their original bytes, make their text searchable, and retrieve the
result through Java or MCP.

Notify.ai uses it for knowledge bases, customer-provided documents, generated reports, reference material,
and other content that an application needs to store and search. The engine owns the artifact
lifecycle; the host application continues to own user authentication, tenant identity, and access
policy.

The Artifact Storage and Retrieval Engine is open-source software. View the source code, report
issues, and contribute on [GitHub](https://github.com/notify-ai-org/artifact-engine).

---

## High-Level Architecture

```text
Application or MCP client
          |
          v
   ArtifactEngine API
          |
     authorization
          |
          v
  durable intake spool
          |
          +----------------------+
          |                      |
          v                      v
   original storage       text processing
          |                      |
          v                      v
      metadata            search index
          \______________________/
                     |
                     v
          retrieval and deletion
```

The major responsibilities are intentionally separated:

- **The application boundary** supplies a trusted principal and tenant for every request.
- **Intake** validates and durably accepts content before long-running processing begins.
- **Original storage** retains the source bytes independently from the searchable representation.
- **Text processing** extracts text, divides it into useful search units, and creates embeddings.
- **Metadata and search** keep lifecycle state and tenant-filtered keyword and semantic indexes.
- **Workers and workflows** run recoverable background work such as storage and indexing.
- **Retrieval** returns metadata, original content, extracted text, or ranked search results.
- **MCP** provides bounded, read-only access for an authenticated, tenant-bound process.

Artifact state is split between storage and indexing. An artifact can therefore be safely accepted
before both background stages finish, and callers can observe whether the original and searchable
representation are ready.

> The diagram describes service boundaries, not internal classes or persistence algorithms. Treat
> the Java interfaces as the supported integration boundary.

---

## Add the Module

Add the engine to the application that will own artifact storage and retrieval:

```xml
<dependency>
  <groupId>dev.notify-ai</groupId>
  <artifactId>artifact-engine</artifactId>
  <version>1.0.0</version>
</dependency>
```

The host application must also provide credentials and infrastructure for the adapters it enables.
Never package production database, S3, or embedding credentials in the application artifact.

---

## Create the Default Engine from an Environment

`DefaultArtifactMcpEngineProvider` is the ready-made environment-driven bootstrap used by the
standalone MCP process. It accepts the engine's own `Environment` abstraction.

### OS environment variables

```java
import dev.notify.artifact.ArtifactEngine;
import dev.notify.artifact.environment.StandardEnvironment;
import dev.notify.artifact.environment.SystemEnvironmentSource;
import dev.notify.artifact.mcp.stdio.DefaultArtifactMcpEngineProvider;

var environment = new StandardEnvironment(new SystemEnvironmentSource());

try (var provider = new DefaultArtifactMcpEngineProvider()) {
  ArtifactEngine engine = provider.createEngine(environment);
  // Pass engine to an application service or MCP gateway.
}
```

`SystemEnvironmentSource` also supports relaxed names. For example, a lookup for
`artifact.s3.bucket` can resolve `ARTIFACT_S3_BUCKET`.

### Command-line arguments

Arguments may use either `--KEY=value` or `--KEY value`:

```java
import dev.notify.artifact.environment.CommandLineEnvironmentSource;
import dev.notify.artifact.environment.StandardEnvironment;

var environment = new StandardEnvironment(
    new CommandLineEnvironmentSource(args)
);
```

Example process arguments:

```bash
java -jar my-app.jar \
  --ARTIFACT_S3_BUCKET=my-private-bucket \
  --ARTIFACT_S3_KMS_KEY_ID=alias/artifact-engine
```

### Java properties file

```java
import dev.notify.artifact.environment.PropertiesFileEnvironmentSource;
import dev.notify.artifact.environment.StandardEnvironment;
import java.nio.file.Path;

var environment = new StandardEnvironment(
    new PropertiesFileEnvironmentSource(Path.of("config/artifact.properties"))
);
```

### Programmatic map

Maps are useful for tests, container launchers, and values obtained from a secret manager:

```java
import dev.notify.artifact.environment.MapEnvironmentSource;
import dev.notify.artifact.environment.StandardEnvironment;
import java.util.Map;

var environment = new StandardEnvironment(
    new MapEnvironmentSource("application", Map.of(
        "ARTIFACT_S3_BUCKET", "my-private-bucket",
        "ARTIFACT_S3_KMS_KEY_ID", "alias/artifact-engine",
        "ARTIFACT_SPOOL_ROOT", "./data/artifacts",
        "EMBEDDING_API_KEY", secretValue
    ))
);
```

### Layer sources with explicit precedence

Sources are checked from first to last. The first source containing a property wins:

```java
var environment = new StandardEnvironment(
    new CommandLineEnvironmentSource(args),
    new SystemEnvironmentSource(),
    new PropertiesFileEnvironmentSource(Path.of("config/artifact.properties")),
    new MapEnvironmentSource("defaults", Map.of(
        "ARTIFACT_S3_REGION", "ap-south-1",
        "ARTIFACT_SPOOL_ROOT", "./data/artifact-spool"
    ))
);

try (var provider = new DefaultArtifactMcpEngineProvider()) {
  ArtifactEngine engine = provider.createEngine(environment);
  runApplication(engine);
}
```

This order lets operators override a file with environment variables or one-off command-line
arguments without changing application code.

> The default MCP provider is a bootstrap adapter and uses fail-closed authorization. A normal
> application should assemble `DefaultArtifactEngine` with its own `AuthorizationService`, as shown
> in the Spring section below. Never treat principal or tenant IDs supplied by an untrusted client
> as authenticated identity.

---

## Use the Engine in an Application

Once the application has an authorized `ArtifactEngine`, use the same facade for the complete
lifecycle:

```java
import dev.notify.artifact.ArtifactEngine;
import dev.notify.artifact.model.Artifact;
import dev.notify.artifact.model.Requests;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class KnowledgeService {
  private final ArtifactEngine artifacts;

  public KnowledgeService(ArtifactEngine artifacts) {
    this.artifacts = artifacts;
  }

  public String addArticle(String principalId, String tenantId, String article) throws Exception {
    byte[] content = article.getBytes(StandardCharsets.UTF_8);

    Artifact accepted = artifacts.ingest(new Requests.Ingest(
        tenantId,
        principalId,
        "article-2026-001",       // idempotency key
        "getting-started.md",
        "text/markdown",
        new ByteArrayInputStream(content),
        content.length,
        Map.of("tags", "docs,onboarding")
    ));

    return accepted.id();
  }

  public List<Requests.SearchHit> search(
      String principalId, String tenantId, String query) {
    return artifacts.search(new Requests.Search(
        tenantId,
        principalId,
        query,
        10,
        List.of("text/markdown"),
        List.of("docs"),
        null
    ));
  }

  public String readText(String principalId, String tenantId, String artifactId) {
    return artifacts.extractedText(principalId, tenantId, artifactId, 32_768);
  }

  public void delete(String principalId, String tenantId, String artifactId) throws Exception {
    artifacts.delete(principalId, tenantId, artifactId);
  }
}
```

Ingestion returns accepted metadata. Storage and indexing may continue asynchronously, so use the
artifact's storage and index statuses when the application needs to wait for readiness. Reuse an
idempotency key only for the same logical content.

---

## Property Reference

The environment-driven default provider recognizes the following properties.

### MCP identity and response controls

| Property | Required | Default | Definition |
|---|---:|---|---|
| `ARTIFACT_MCP_PRINCIPAL_ID` | MCP only | — | Trusted principal bound to the MCP process. It is not accepted from individual tool calls. |
| `ARTIFACT_MCP_TENANT_ID` | MCP only | — | Tenant bound to the MCP process and all of its artifact operations. |
| `ARTIFACT_MCP_SCOPES` | MCP only | — | Comma-separated scopes. Supported values are `artifact.search`, `artifact.metadata`, `artifact.text`, `artifact.content`, and the wildcard `artifact.*`. |
| `ARTIFACT_MCP_MAX_TEXT_CHARACTERS` | No | `32768` | Maximum characters returned by text operations. Must be positive and cannot exceed the engine's 1,000,000-character ceiling. |
| `ARTIFACT_MCP_MAX_CONTENT_BYTES` | No | `262144` | Maximum original-content bytes returned in one Base64 segment. Must be positive and cannot exceed the 4 MiB library ceiling. |
| `ARTIFACT_MCP_REQUEST_TIMEOUT_SECONDS` | No | `30` | Positive MCP request timeout in seconds. |

### PostgreSQL and vector search

| Property | Required | Default | Definition |
|---|---:|---|---|
| `ARTIFACT_JDBC_URL` | No | — | JDBC URL for durable PostgreSQL metadata and vector stores. If absent, `JDBC_DATABASE_URL` is checked; if both are absent, process-local stores are used. |
| `JDBC_DATABASE_URL` | No | — | Fallback for `ARTIFACT_JDBC_URL`. |
| `ARTIFACT_JDBC_USER` | With secured JDBC | — | PostgreSQL username. Falls back to `DB_USER`. |
| `DB_USER` | No | — | Fallback database username. |
| `ARTIFACT_JDBC_PASSWORD` | With secured JDBC | empty | PostgreSQL password. Falls back to `DB_PASSWORD`. |
| `DB_PASSWORD` | No | empty | Fallback database password. |
| `ARTIFACT_JDBC_MAX_POOL_SIZE` | No | `8` | Positive maximum number of JDBC connections in the pool. |
| `ARTIFACT_JDBC_MIN_IDLE` | No | `1` | Positive minimum number of idle JDBC connections. |
| `ARTIFACT_VECTOR_DIMENSIONS` | No | `1536` | Expected embedding vector size. It must match both the embedding model output and the database vector column. |

Apply all artifact database migrations before enabling the JDBC URL. PostgreSQL and pgvector are
the production path; process-local stores are for development and do not survive a restart.

### S3 original-content storage

| Property | Required | Default | Definition |
|---|---:|---|---|
| `ARTIFACT_S3_BUCKET` | Yes | — | Private S3 bucket that retains original artifact bytes. |
| `ARTIFACT_S3_KMS_KEY_ID` | Yes | — | AWS KMS key ID or alias used for server-side encryption. |
| `ARTIFACT_S3_REGION` | No | `ap-south-1` | AWS region used to create the S3 client. |
| `ARTIFACT_S3_ENVIRONMENT` | No | `default` | Deployment namespace included in validated object keys. Use a stable value such as `dev`, `staging`, or `production`. |
| `ARTIFACT_S3_EXPECTED_BUCKET_OWNER` | No | — | AWS account ID expected to own the bucket. Recommended to prevent requests reaching an unintended bucket. |
| `ARTIFACT_S3_BUCKET_KEY_ENABLED` | No | `true` | Enables S3 Bucket Keys for SSE-KMS requests when supported by the bucket. Accepts `true` or `false`. |

AWS credentials use the standard AWS SDK credentials provider chain. Keep the bucket private,
enable versioning according to the application's recovery policy, and restrict the runtime role to
the configured bucket and KMS key.

### Durable intake spool

| Property | Required | Default | Definition |
|---|---:|---|---|
| `ARTIFACT_SPOOL_ROOT` | No | `./data/artifact-spool` | Durable local directory used while artifacts are accepted and processed. |
| `ARTIFACT_SPOOL_MAX_ARTIFACT_BYTES` | No | `134217728` | Positive maximum size of one artifact in bytes (128 MiB by default). |

Place the spool on persistent storage with adequate capacity and filesystem permissions. Do not use
an ephemeral temporary directory in production.

### Embeddings

| Property | Required | Default | Definition |
|---|---:|---|---|
| `EMBEDDING_BASE_URL` | No | `https://api.openai.com/v1` | Base URL of an OpenAI-compatible embeddings API. |
| `EMBEDDING_API_PATH` | No | `/embeddings` | Embeddings endpoint path resolved against the base URL. |
| `EMBEDDING_API_KEY` | For authenticated endpoint | — | API key sent to the embedding provider. Falls back to `OPENAI_API_KEY`. |
| `OPENAI_API_KEY` | No | — | Fallback embedding API key. |
| `EMBEDDING_QUERY_MODEL` | No | first configured model or `text-embedding-3-small` | Model used to embed search queries and the primary model added to the provider set. |
| `EMBEDDING_MODELS` | No | — | Comma-separated models available to the embedding service. The query model is always included. |
| `EMBEDDING_MODEL_VERSION` | No | query model name | Version label stored with vectors created by the query model. Change it when model behavior or indexing compatibility changes. |
| `EMBEDDING_MAX_BATCH_SIZE` | No | `32` | Positive maximum number of texts in one embedding request. |
| `EMBEDDING_MAX_WAIT_MILLIS` | No | `25` | Positive batching window in milliseconds before a partially filled batch is sent. |
| `EMBEDDING_CACHE_MAX_ENTRIES` | No | `10000` | Positive maximum number of vectors retained in the process-local embedding cache. |
| `EMBEDDING_CACHE_TTL_SECONDS` | No | `3600` | Positive lifetime of successful cached embeddings in seconds. |
| `EMBEDDING_CONNECT_TIMEOUT_SECONDS` | No | `10` | Positive HTTP connection timeout in seconds. |
| `EMBEDDING_TIMEOUT_SECONDS` | No | `30` | Positive read, write, call, and overall request timeout in seconds. |

All numeric properties above must be positive integers. The returned embedding size is validated
against `ARTIFACT_VECTOR_DIMENSIONS` before a vector is stored.

## Production Checklist

- Derive principal and tenant IDs from authenticated server-side context.
- Apply all database migrations and keep vector dimensions aligned with the embedding model.
- Use a private S3 bucket, SSE-KMS, least-privilege AWS permissions, and expected-owner validation.
- Put the spool on durable storage and monitor its capacity.
- Run storage and indexing workers and monitor retry and dead-letter states.
- Keep API keys, database credentials, and KMS configuration outside source control.
- Treat extracted document text as untrusted data when it is passed to an agent or model.
- Bound text and binary retrieval sizes at every external interface.

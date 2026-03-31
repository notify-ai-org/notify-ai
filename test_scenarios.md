# Test Scenarios and Edge Cases

A comprehensive test plan covering the `EventConsumer` payload ingestion, Agent LLM extraction into Facts, and the `DefaultMemoryAssembler` vectorization capabilities.

## 1. Event Consumer (`EventConsumer.java`)

### Positive Path Scenarios
1. **Single Valid Event Processing**: Submit a standard JSON array containing 1 valid `EventCapture`. Verify HTTP 202 Accepted, and verify that the reactive `Flowable` successfully queries the LLM and persists `AgentThoughtProcess` and `bulletReasons`.
2. **Batch Ingestion**: Submit a JSON array of 100 `EventCapture` elements. Verify seamless asynchronous reactive throughput mapping without ThreadPool exhaustion or HTTP blocking.
3. **Valid Agent Suppressions**: Inject an event mathematically guaranteed to trigger a refusal/suppression (e.g., background noise event). Verify the LLM outputs `{"result": "suppressed"}` and the system properly short-circuits safely, saving no facts.
4. **Valid Facts Parsing**: Inject a high-density event payload. Verify the LLM correctly groups the data into the `{ reasoning: { factsUsed: [...] }}` JSON structure, and verify the `FactRepository` receives precisely mapped `FactEntity` records tied to the origin `correlationId`.

### Edge Cases & Vulnerabilities
1. **Empty/Null Payload Injection**: Send HTTP POST requests featuring an empty array `[]` or outright nulls. Ensure `ResponseEntity.badRequest()` triggers cleanly.
2. **Malformed Agent Fallbacks**: Simulate the LLM agent hallucinating or emitting broken JSON configurations (e.g., missing quotes or raw text conversational outputs). The internal `try-catch` JSON deserializer must catch the format error, skip the node, and log the failure without crashing the overarching RxJava Flowable chain.
3. **Missing Nested Attributes**: Simulate the LLM generating the correct JSON shell but entirely omitting properties like `thoughtProcess` or `factsUsed`. The iteration checks (e.g., `.containsKey("thoughtProcess")`) must safely bypass the missing objects instead of throwing `NullPointerException`.
4. **Client Context Fallback**: Initiate an API call stripped of typical authentication keys inside `AgentContextHolder` (or using the `DEFAULT_CLIENT_ID`). Ensure `FactEntity` safely triggers its fallback conditional `ctxNow.getSource() : "default"` instead of crashing.
5. **Prompt Token Budget Overflow**: Submit an `EventCapture` JSON sequence massive enough to breach the `maxLimitsSysProp` budget constraint. Ensure the assembler gracefully truncates or rejects the prompt instead of causing LLM `ContextExhaustion` exceptions.

---

## 2. Fact Persistence (`FactRepository` / DB)

### Positive Path Scenarios
1. **Accurate Lineage Tracking**: Verify that a completely finalized `FactEntity` tracks `clientId`, `sourceType = ACP_EVENT_PROCESSOR`, and precisely matches the parent event's `correlationId`.
2. **MySQL Upsert Compatibility**: Verify `FactRepository.upsertNative` triggers MySQL-safe `ON DUPLICATE KEY UPDATE` grammar, replacing identical primary facts iteratively without duplicates.

### Edge Cases & Vulnerabilities
1. **String/Column Overflow Bounds**: If the LLM generates a mathematically colossal string for a single bullet fact, verify if it snaps the target DB column boundaries (e.g., > 255 chars or > TEXT capacity).
2. **Concurrency Upsert Locks**: Rapidly execute identical parallel ingestion requests containing intersecting `FactEntity` IDs simultaneously. Verify Deadlocks (`HTTP 500`) are prevented or retried automatically by the connection pool.

---

## 3. Memory Assembly & Search (`DefaultMemoryAssembler.java`)

### Positive Path Scenarios
1. **Memory Page Creation**: Dispatch a new Fact for an unknown correlation mapping. Verify a nascent `MemoryPage` initiates successfully.
2. **Incremental Fact Appending**: Fire 5 rapid, successive Facts inside the `windowSize` bounds (e.g., `1h`). Verify the string facts append consecutively into the raw summary view instead of initializing 5 different pages.
3. **Synchronous Vector Embeddings**: After appending a fact, verify `embeddingService.embed()` returns valid `float[]` vectors and properly mutates the `MemoryPage.embedding` payload.
4. **KNN Vector Semantic Searching**: Run `memoryAssembler.search(queryText, ...)` using a human-like phrase heavily related to recently saved facts. Verify the backend Postgres PGVector/KNN query safely executes spatial comparisons and identifies the correct `MemoryPage`.

### Edge Cases & Vulnerabilities
1. **Memory Page Time/Size Expiration Rules**: Programmatically advance the system clock beyond `inactivityTimeout` (e.g., `30m`) or push greater than `maxFactsPerPage` (e.g., 50). Verify the original MemoryPage safely "seals", and the 51st Fact spawns an entirely pristine target memory space!
2. **Embedding Architecture Failures (Circuit Breaks)**: Simulate `embeddingService.embed()` throwing an artificial timeout or returning `Mono.error()`. Verify `memoryAssembler.appendFact()` correctly intercepts the catastrophic failure, cancelling the DB Upsert to ensure the system retries embedding later (Preventing invisible/un-searchable saved memory).
3. **Null Embedding Arrays**: Simulate an LLM embedding output providing `null` arrays (`EmbeddingResult("hash-1", null)`). Verify the `search()` array handles iterating the fallback safely and bypasses `knnSearch`, resolving as an empty `List.of()` instead of a Null Pointer cascade.
4. **Massive Context Appending Scale Limits**: Overload a single memory page with huge raw bullet strings. Determine if appending giant strings across vectors dilutes the cosine similarity hit-rate over time, necessitating aggressive `maxFactsPerPage` downsizing.

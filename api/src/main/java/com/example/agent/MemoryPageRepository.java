package com.example.agent;

import com.example.agent.records.MemoryPage;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.ArrayOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;

import jakarta.annotation.PostConstruct;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class MemoryPageRepository {

    private static final Logger log = LoggerFactory.getLogger(MemoryPageRepository.class);
    private static final String INDEX = "idx:mem:page";
    /**
     * Similarity threshold. Pages with cosine similarity >= this value are
     * considered duplicates.
     * In RediSearch KNN with COSINE metric, the score is the distance (1 -
     * similarity).
     */
    private static final double SIMILARITY_THRESHOLD = 0.95;

    private final StatefulRedisConnection<String, String> connection;

    @Value("${redis.vector.dim:3072}")
    private int vectorDim;

    public MemoryPageRepository(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    private enum RediSearchCommand implements ProtocolKeyword {
        FT_SEARCH("FT.SEARCH"),
        FT_CREATE("FT.CREATE"),
        FT_LIST("FT._LIST");

        private final byte[] bytes;

        RediSearchCommand(String name) {
            this.bytes = name.getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }
    }

    @PostConstruct
    void ensureIndex() {
        RedisCommands<String, String> cmd = connection.sync();

        // Check if the index already exists using FT._LIST
        try {
            List<Object> indexes = cmd.dispatch(
                    RediSearchCommand.FT_LIST,
                    new ArrayOutput<>(StringCodec.UTF8),
                    new CommandArgs<>(StringCodec.UTF8));
            if (indexes != null && indexes.stream().anyMatch(o -> INDEX.equals(o.toString()))) {
                log.info("RediSearch index '{}' already exists, skipping creation.", INDEX);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not list RediSearch indexes, will attempt to create '{}': {}", INDEX, e.getMessage());
        }

        // Create the index
        try {
            cmd.dispatch(
                    RediSearchCommand.FT_CREATE,
                    new io.lettuce.core.output.StatusOutput<>(StringCodec.UTF8),
                    new CommandArgs<>(StringCodec.UTF8)
                            .add(INDEX)
                            .add("ON").add("HASH")
                            .add("SCHEMA")
                            .add("namespace").add("TAG")
                            .add("correlationId").add("TAG")
                            .add("createdAt").add("NUMERIC")
                            .add("severityMax").add("TEXT")
                            .add("summary").add("TEXT")
                            .add("embedding").add("VECTOR").add("HNSW")
                            .add("6")
                            .add("TYPE").add("FLOAT32")
                            .add("DIM").add(String.valueOf(vectorDim))
                            .add("DISTANCE_METRIC").add("COSINE"));
            log.info("Created RediSearch index '{}' with VECTOR DIM={}.", INDEX, vectorDim);
        } catch (Exception e) {
            // Index may already exist if concurrent startup — log and continue
            log.warn("Failed to create RediSearch index '{}': {}", INDEX, e.getMessage());
        }
    }

    public record SearchResult(MemoryPage page, double score) {
    }

    public Optional<MemoryPage> findOpenPage(String namespace, Instant windowStart) {
        String pageId = namespace + ":" + windowStart.toString();
        RedisCommands<String, String> cmd = connection.sync();
        Map<String, String> fields = cmd.hgetall(pageId);

        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }

        String correlationId = fields.getOrDefault("correlationId", "");
        long createdAtMilli = Long.parseLong(fields.getOrDefault("createdAt", "0"));
        String severityMax = fields.getOrDefault("severityMax", "");
        String summary = fields.getOrDefault("summary", "");

        MemoryPage page = new MemoryPage(
                pageId,
                namespace,
                correlationId,
                null, // pageType
                summary,
                severityMax,
                windowStart,
                0.0,
                0.0,
                Instant.ofEpochMilli(createdAtMilli),
                Instant.now(),
                Collections.emptySet(),
                Collections.emptyList(),
                null,
                null);

        return Optional.of(page);
    }

    /*
     * =========================
     * UPSERT
     * =========================
     */

    public void upsert(MemoryPage page, Duration ttl) {
        // Step 1: Check for duplicates using cosine similarity search
        if (isDuplicate(page)) {
            // Duplicate found based on cosine similarity, skipping insertion
            return;
        }

        RedisCommands<String, String> cmd = connection.sync();

        Map<String, String> fields = new HashMap<>();
        fields.put("namespace", page.namespace());
        fields.put("correlationId", page.correlationId());
        fields.put("createdAt", page.createdAt() != null ? String.valueOf(page.createdAt().toEpochMilli()) : "0");
        fields.put("severityMax", page.severityMax());
        fields.put("summary", page.summary());

        // Vector must be stored as binary-safe string
        fields.put("embedding", encodeVector(page.embedding()));

        cmd.hset(page.pageId(), fields);

        if (ttl != null) {
            cmd.expire(page.pageId(), ttl.getSeconds());
        }
    }

    private boolean isDuplicate(MemoryPage page) {
        if (page.embedding() == null) {
            return false;
        }

        // Search for the single most similar page for this tenant
        List<SearchResult> results = knnSearch(
                page.embedding(),
                1,
                Optional.ofNullable(page.namespace()),
                Optional.ofNullable(page.correlationId()));

        if (results.isEmpty()) {
            return false;
        }

        // RediSearch COSINE distance: score = 1 - similarity
        double distance = results.get(0).score();
        double similarity = 1.0 - distance;

        return similarity >= SIMILARITY_THRESHOLD;
    }

    private static String encodeVector(byte[] vector) {
        return new String(vector, StandardCharsets.ISO_8859_1);
    }

    private static String encodeVector(float[] vector) {
        if (vector == null)
            return "";
        ByteBuffer buf = ByteBuffer.allocate(vector.length * 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buf.putFloat(v);
        }
        return encodeVector(buf.array());
    }

    public List<SearchResult> knnSearch(
            float[] queryVector,
            int k,
            Optional<String> namespace,
            Optional<String> correlationId) {

        RedisCommands<String, String> cmd = connection.sync();

        StringBuilder filter = new StringBuilder("*");

        namespace.ifPresent(ns -> filter.append(" @namespace:{").append(escape(ns)).append("}"));
        correlationId.ifPresent(cid -> filter.append(" @correlationId:{").append(escape(cid)).append("}"));

        String query = filter + "=>[KNN " + k + " @embedding $vec AS score]";

        List<Object> res = cmd.dispatch(
                RediSearchCommand.FT_SEARCH,
                new ArrayOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(INDEX)
                        .add(query)
                        .add("PARAMS").add("2").add("vec").add(encodeVector(queryVector))
                        .add("SORTBY").add("score")
                        .add("RETURN").add("6")
                        .add("namespace")
                        .add("correlationId")
                        .add("createdAt")
                        .add("severityMax")
                        .add("summary")
                        .add("score")
                        .add("DIALECT").add("2"));

        return parseResults(res);
    }

    /**
     * Parses FT.SEARCH results returned in DIALECT 2 (RESP3-style) format.
     *
     * The raw list is a map-like structure:
     * [attributes, [], format, STRING, results, [...], total_results, N, warning,
     * []]
     *
     * Each entry inside the "results" list is itself a map-like list:
     * [id, "docKey", extra_attributes, [field1, val1, field2, val2, ...]]
     */
    @SuppressWarnings("unchecked")
    private List<SearchResult> parseResults(List<Object> raw) {
        List<SearchResult> results = new ArrayList<>();
        if (raw == null || raw.size() < 2) {
            return results;
        }

        // --- Extract the "results" list from the envelope ---
        List<Object> resultEntries = null;
        for (int i = 0; i < raw.size() - 1; i += 2) {
            String envKey = raw.get(i).toString();
            if ("results".equals(envKey)) {
                Object val = raw.get(i + 1);
                if (val instanceof List) {
                    resultEntries = (List<Object>) val;
                }
                break;
            }
        }
        if (resultEntries == null || resultEntries.isEmpty()) {
            return results;
        }

        // --- Iterate over each result entry ---
        for (Object entry : resultEntries) {
            if (!(entry instanceof List)) {
                continue;
            }
            List<Object> entryList = (List<Object>) entry;

            // Each entry is a map-like list: [id, "docKey", extra_attributes, [...]]
            String key = "";
            List<Object> fields = null;
            for (int i = 0; i < entryList.size() - 1; i += 2) {
                String entryKey = entryList.get(i).toString();
                if ("id".equals(entryKey)) {
                    key = entryList.get(i + 1).toString();
                } else if ("extra_attributes".equals(entryKey)) {
                    Object attrVal = entryList.get(i + 1);
                    if (attrVal instanceof List) {
                        fields = (List<Object>) attrVal;
                    }
                }
            }

            if (fields == null || fields.isEmpty()) {
                continue;
            }

            String namespace = "";
            String correlationId = "";
            long createdAtMilli = 0;
            String severityMax = "";
            String summary = "";
            double score = 1.0;

            for (int f = 0; f < fields.size() - 1; f += 2) {
                String name = fields.get(f).toString();
                Object valObj = fields.get(f + 1);
                if (valObj == null)
                    continue;
                String val = valObj.toString();

                switch (name) {
                    case "namespace" -> namespace = val;
                    case "correlationId" -> correlationId = val;
                    case "createdAt" -> createdAtMilli = Long.parseLong(val);
                    case "severityMax" -> severityMax = val;
                    case "summary" -> summary = val;
                    case "score" -> score = Double.parseDouble(val);
                }
            }

            MemoryPage page = new MemoryPage(
                    key, namespace, correlationId,
                    null, summary, severityMax, java.time.Instant.now(), 0.0, 0.0,
                    java.time.Instant.ofEpochMilli(createdAtMilli), java.time.Instant.now(),
                    Collections.emptySet(), Collections.emptyList(), null, null);
            results.add(new SearchResult(page, score));
        }
        return results;
    }

    private String escape(String s) {
        return s.replaceAll("([{}|\\-])", "\\\\$1");
    }
}

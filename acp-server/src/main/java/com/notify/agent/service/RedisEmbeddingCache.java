package com.notify.agent.service;

import com.notify.agent.interfaces.EmbeddingCache;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class RedisEmbeddingCache implements EmbeddingCache {

    private final io.lettuce.core.api.reactive.RedisReactiveCommands<String, byte[]> cmd;

    public RedisEmbeddingCache(io.lettuce.core.api.reactive.RedisReactiveCommands<String, byte[]> cmd) {
        this.cmd = cmd;
    }

    private String key(String model, String schemaVersion, String textHash) {
        return "emb:cache:" + model + ":v" + schemaVersion + ":" + textHash;
    }

    @Override
    public Mono<float[]> get(String model, String schemaVersion, String textHash) {
        return cmd.get(key(model, schemaVersion, textHash))
                .map(VectorCodec::fromBytes)
                .switchIfEmpty(Mono.empty());
    }

    @Override
    public Mono<Void> put(String model, String schemaVersion, String textHash, float[] vector, Duration ttl) {
        String k = key(model, schemaVersion, textHash);
        return cmd.set(k, VectorCodec.toBytes(vector))
                .then(cmd.expire(k, ttl.getSeconds()))
                .then();
    }

    public final class VectorCodec {
        public static byte[] toBytes(float[] vector) {
            var buf = java.nio.ByteBuffer.allocate(vector.length * 4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (float v : vector)
                buf.putFloat(v);
            return buf.array();
        }

        public static float[] fromBytes(byte[] bytes) {
            var buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int n = bytes.length / 4;
            float[] v = new float[n];
            for (int i = 0; i < n; i++)
                v[i] = buf.getFloat();
            return v;
        }

        private VectorCodec() {
        }
    }
}

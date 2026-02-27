package com.example.agent.records;

public record EmbeddingRequest(
                String namespace,
                String pageId,
                String text,
                String model,
                String schemaVersion,
                String textHash) {
}

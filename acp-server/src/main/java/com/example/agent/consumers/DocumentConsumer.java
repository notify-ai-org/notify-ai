package com.example.agent.consumers;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentConsumer {

    private final VectorStore vectorStore;

    @PostMapping("/ingest")
    public void consume(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Received document for ingestion via REST API");
            String content = (String) payload.get("content");
            Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");

            if (content != null) {
                Document document = new Document(content, metadata);
                vectorStore.add(List.of(document));
                log.info("Document added to vector store");
            }
        } catch (Exception e) {
            log.error("Error processing document", e);
        }
    }

}

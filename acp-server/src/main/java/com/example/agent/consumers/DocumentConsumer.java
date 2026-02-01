package com.example.agent.consumers;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.agent.AgentContextHolder;
import com.example.agent.models.AgentContext;
import com.google.genai.types.Content;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentConsumer {

    @PostMapping("/ingest")
    public void consume(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Received document for ingestion via REST API");
            String content = (String) payload.get("content");
            Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
            if (content != null) {
                AgentContext agentContext = AgentContextHolder.getContext();
                agentContext.setContent(Content.fromParts(com.google.genai.types.Part.fromText(content)));
            }
        } catch (Exception e) {
            log.error("Error processing document", e);
        }
    }

}

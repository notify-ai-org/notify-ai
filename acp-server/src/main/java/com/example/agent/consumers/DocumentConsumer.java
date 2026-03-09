package com.example.agent.consumers;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;

import com.example.agent.AgentContextHolder;
import com.example.agent.models.AgentContext;
import com.google.genai.types.Content;
import com.example.agent.exceptions.AgentApplicationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
@Slf4j
public class DocumentConsumer {

    @PostMapping("/ingest")
    public Mono<Void> consume(@RequestBody Map<String, Object> payload) {
        return Mono.fromRunnable(() -> {
            try {
                log.info("Received document for ingestion via REST API");
                String content = (String) payload.get("content");
                if (content != null) {
                    AgentContext agentContext = AgentContextHolder.getContext();
                    agentContext.setContent(Content.fromParts(com.google.genai.types.Part.fromText(content)));
                }
            } catch (Exception e) {
                log.error("Error processing document", e);
                throw new AgentApplicationException("Error processing document", e);
            }
        });
    }

}

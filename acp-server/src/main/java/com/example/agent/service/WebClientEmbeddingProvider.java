package com.example.agent.service;

import com.example.agent.interfaces.EmbeddingProvider;
import com.example.agent.records.EmbeddingResult;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WebClientEmbeddingProvider implements EmbeddingProvider {

    private final WebClient webClient;

    public WebClientEmbeddingProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<List<EmbeddingResult>> embedBatch(String model, List<String> texts, List<String> textHashes) {
        var body = java.util.Map.of(
                "model", model,
                "input", texts);

        return webClient.post()
                .uri("/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .map(resp -> parse(resp, model, textHashes));
    }

    @SuppressWarnings("unchecked")
    private List<EmbeddingResult> parse(Map<String, Object> resp, String model, List<String> textHashes) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
        List<EmbeddingResult> out = new ArrayList<>(data.size());

        for (int i = 0; i < data.size(); i++) {
            List<Double> emb = (List<Double>) data.get(i).get("embedding");
            float[] v = new float[emb.size()];
            for (int j = 0; j < emb.size(); j++)
                v[j] = emb.get(j).floatValue();
            out.add(new EmbeddingResult(model, textHashes.get(i), v));
        }
        return out;
    }
}

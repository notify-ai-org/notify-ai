package com.example.agent;

import com.example.sdk.model.*;
import java.net.http.*;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Initializes SDK and sends discovered metadata to Agent Server.
 */
public class AgentBootstrapper {

    private final String basePackage;
    private final String agentServerUrl;

    public AgentBootstrapper(String basePackage, String agentServerUrl) {
        this.basePackage = basePackage;
        this.agentServerUrl = agentServerUrl;
    }

    public void bootstrap() {
        AnnotationProcessor processor = new AnnotationProcessor(basePackage);
        processor.process();

        List<ClassModel> classModels = processor.getClassModels();
        Collection<VocabularyModel> vocabularies = processor.getVocabularyModels();

        sendToAgentServer("/class-models", classModels);
        sendToAgentServer("/vocabularies", vocabularies);

        System.out.println("✅ SDK Bootstrapped. Metadata sent to Agent Server.");
    }

    private void sendToAgentServer(String path, Object payload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(agentServerUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

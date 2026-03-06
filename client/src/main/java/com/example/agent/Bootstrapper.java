package com.example.agent;

import com.example.agent.config.NotifyProperties;
import com.example.agent.models.ClassModel;
import com.example.agent.models.ClientRegistrationDto;
import com.example.agent.models.EventCapture;
import com.example.agent.models.TokenRefreshDto;
import com.example.agent.models.metadata.EventMetadata;
import com.example.agent.models.metadata.RuleMetadata;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bootstraps the Notification Engine SDK: runs AnnotationProcessor and
 * VocabularyManager, collects client metadata and client id, initializes
 * Buffer,
 * registers with acp-server, obtains token, enqueues vocabulary/rules into the
 * Buffer, starts the Dispatcher, and handles graceful shutdown.
 */
public class Bootstrapper {

    private final NotifyProperties props;
    private final AnnotationProcessor annotationProcessor;
    private final VocabularyManager vocabularyManager;
    private final Buffer buffer;
    private final AcpServerClient acpClient;
    private final TokenHolder tokenHolder;
    private final InvokeManager invokeManager;
    private final MetricsManager metricsManager;

    private String clientId;
    private Dispatcher dispatcher;
    private Thread dispatcherThread;

    public Bootstrapper(NotifyProperties props,
            AnnotationProcessor annotationProcessor,
            VocabularyManager vocabularyManager,
            Buffer buffer,
            AcpServerClient acpClient,
            TokenHolder tokenHolder,
            InvokeManager invokeManager,
            MetricsManager metricsManager) {
        this.props = props;
        this.annotationProcessor = annotationProcessor;
        this.vocabularyManager = vocabularyManager;
        this.buffer = buffer;
        this.acpClient = acpClient;
        this.tokenHolder = tokenHolder;
        this.invokeManager = invokeManager;
        this.metricsManager = metricsManager;
    }

    /**
     * Called by Spring initMethod. Runs scanning, registration, enqueue, and starts
     * Dispatcher.
     */
    public void bootstrap() {
        annotationProcessor.process();
        invokeManager.buildFrom(annotationProcessor);

        clientId = getOrCreateClientId();

        try {
            ClientRegistrationDto.Request reg = new ClientRegistrationDto.Request();
            reg.setClientId(clientId);
            reg.setApplicationName(props.getApplicationName());
            reg.setBasePackage(props.getBasePackage());
            ClientRegistrationDto.Response resp = acpClient.register(reg, null);
            if (resp != null && resp.getToken() != null) {
                tokenHolder.setTokens(resp.getToken(), resp.getRefreshToken(), resp.getExpiresInMs());
            }
        } catch (Exception e) {
            // acp-server may not have /api/client/register; continue without auth
        }

        List<EventMetadata> events = annotationProcessor.getEvents();
        if (!events.isEmpty()) {
            events.forEach(
                    (event) -> {
                        EventCapture dto = new EventCapture();
                        dto.setEvent(event.getEvent());
                        dto.getEvent().setEventType("USER");
                        dto.setOccuredAt(Instant.now());
                        dto.setPayload(vocabularyManager.toFlattenedMap(event));
                        dto.setServiceName(event.getDeclaringClass().getSimpleName());
                        buffer.addEventCapture(dto);
                    });
        }

        List<ClassModel> vocab = vocabularyManager.toClassModelDtoList();
        if (!vocab.isEmpty())
            buffer.addVocabulary(vocab);

        for (RuleMetadata r : annotationProcessor.getRules()) {
            String ev = (r.getEvent() != null && !r.getEvent().isEmpty()) ? r.getEvent() : "*";
            buffer.addRule(ev, r.getName(), r.getDescription(), null);
        }

        dispatcher = new Dispatcher(buffer, acpClient, tokenHolder::getToken, this::refreshToken);
        dispatcherThread = new Thread(dispatcher, "notify-dispatcher");
        dispatcherThread.setDaemon(false);
        dispatcherThread.start();
    }

    private String getOrCreateClientId() {
        String path = props.getClientIdPath();
        Path p = (path != null && !path.isEmpty()) ? Paths.get(path)
                : Paths.get(System.getProperty("java.io.tmpdir"), "notify-client-id.txt");
        try {
            if (Files.exists(p))
                return Files.readString(p).trim();
            String id = props.getApplicationName() + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (p.getParent() != null)
                Files.createDirectories(p.getParent());
            Files.writeString(p, id);
            return id;
        } catch (IOException e) {
            return props.getApplicationName() + "-" + UUID.randomUUID().toString();
        }
    }

    private void refreshToken() {
        String ref = tokenHolder.getRefreshToken();
        if (ref == null || ref.isEmpty())
            return;
        try {
            TokenRefreshDto.Request req = new TokenRefreshDto.Request();
            req.setClientId(clientId);
            req.setRefreshToken(ref);
            TokenRefreshDto.Response resp = acpClient.refreshToken(req);
            tokenHolder.setToken(resp.getToken(), resp.getExpiresInMs());
        } catch (Exception ignored) {
        }
    }

    @PreDestroy
    public void shutdown() {
        if (dispatcher != null)
            dispatcher.stop();
        if (dispatcherThread != null) {
            try {
                dispatcherThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (metricsManager != null && acpClient != null) {
            metricsManager.sendToAcpServer(acpClient, tokenHolder != null ? tokenHolder.getToken() : null);
        }
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public InvokeManager getInvokeManager() {
        return invokeManager;
    }

    public MetricsManager getMetricsManager() {
        return metricsManager;
    }

    public AnnotationProcessor getAnnotationProcessor() {
        return annotationProcessor;
    }
}

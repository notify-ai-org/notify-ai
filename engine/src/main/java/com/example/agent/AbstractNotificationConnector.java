package com.example.agent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.example.agent.models.ConnectorMetrics;
import com.example.agent.models.ConnectorProperties.ChannelConfig;
import com.example.agent.models.NotificationConnector;
import com.example.agent.models.NotificationJob;

public abstract class AbstractNotificationConnector implements NotificationConnector {

    protected ChannelConfig configuration = new ChannelConfig();

    private DeadLetterManagerImpl deadLetterManager;

    protected AtomicReference<ConnectorMetrics> metrics = new AtomicReference<>();

    protected List<String> logs = new CopyOnWriteArrayList<>();

    @Override
    public void bind(ChannelConfig configuration) {
        this.configuration = configuration;
    }

    @Override
    public void init(AtomicReference<ConnectorMetrics> metrics) {
        this.metrics = metrics;
        logs.add("Initialized connector for " + channel());
    }
    

    protected void retryWithBackoff(NotificationJob job, Runnable action) {
        long delay = configuration.getDelay();
        int maxAttempts = configuration.getMaxAttempts();
        int backOffMultiplier = configuration.getBackOffMultiplier();
        Instant firstAttemptAt = Instant.now();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                metrics.get().markSent();
                return;
            } catch (Exception ex) {
                metrics.get().markRetried();
                logs.add("Retry " + attempt + " failed for job " + job.getId());

                if (attempt == maxAttempts) {
                    metrics.get().markFailed();
                    deadLetterManager.enqueue(
                        job,
                        ex,
                        attempt,
                        firstAttemptAt,
                        Instant.now(),
                        "worker-" + Thread.currentThread().getName(),
                        "dispatcher-1",
                        null,
                        job.getTemplate()
                    );
                    throw ex;
                }

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {}

                delay *= backOffMultiplier;
            }
        }
    }
}

package com.notify.agent.planner;

import com.notify.agent.AgentOrchestrator;
import com.notify.agent.SessionEventRepository;
import com.notify.agent.annotations.ManagedConfiguration;
import com.notify.agent.annotations.ManagedConfiguration.ConfigSource;
import com.notify.agent.config.AgentRegistry;
import com.notify.agent.interfaces.TokenEstimator;
import com.notify.agent.models.AgentContext;
import com.notify.agent.models.SessionEventEntity;
import com.notify.agent.records.ContextBundle;
import com.notify.agent.records.DecisionRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enriches a {@link ContextBundle} with recent ADK session event history.
 * <p>
 * Retrieves {@link SessionEventEntity} rows for the session within a
 * managed look-back window. If the formatted history fits in the remaining
 * token budget, it is included verbatim; otherwise the EventSummarizer
 * agent is invoked to produce a compact summary.
 */
@Component
public class EventHistoryPlanner {

    private static final Logger log = LoggerFactory.getLogger(EventHistoryPlanner.class);

    /** Look-back window in minutes — managed via ManagedConfigService / DB. */
    @Value("${event.history.interval.minutes:60}")
    @ManagedConfiguration(key = "event.history.interval.minutes", source = ConfigSource.DB)
    private int historyIntervalMinutes = 60;

    private final SessionEventRepository sessionEventRepository;
    private final TokenEstimator tokenEstimator;
    private final AgentOrchestrator agentOrchestrator;
    private final AgentRegistry agentRegistry;

    public EventHistoryPlanner(SessionEventRepository sessionEventRepository,
                               TokenEstimator tokenEstimator,
                               AgentOrchestrator agentOrchestrator,
                               AgentRegistry agentRegistry) {
        this.sessionEventRepository = sessionEventRepository;
        this.tokenEstimator = tokenEstimator;
        this.agentOrchestrator = agentOrchestrator;
        this.agentRegistry = agentRegistry;
    }

    /**
     * Enrich the provided bundle with session event history.
     *
     * @param bundle          the base bundle produced by {@link DefaultRetrievalPlanner}
     * @param req             the original decision request (carries sessionId)
     * @param remainingTokens tokens still available after core context assembly
     * @return a new {@link ContextBundle} with event history fields populated
     */
    public ContextBundle enrich(ContextBundle bundle, DecisionRequest req, int remainingTokens) {
        if (req.sessionId() == null || req.sessionId().isBlank()) {
            return bundle; // no session → nothing to enrich
        }

        Instant since = Instant.now().minusSeconds((long) historyIntervalMinutes * 60);
        List<SessionEventEntity> events =
                sessionEventRepository.findBySession_SessionIdAndOccurredAtAfterOrderByOccurredAtAsc(
                        req.sessionId(), since);

        if (events.isEmpty()) {
            return bundle;
        }

        List<String> summaries = formatEvents(events);
        String combined = String.join("\n", summaries);
        int estimatedTokens = tokenEstimator.estimateTokens(combined);

        if (estimatedTokens <= remainingTokens) {
            log.debug("EventHistoryPlanner: {} events ({} tokens) fit in remaining budget of {}",
                    events.size(), estimatedTokens, remainingTokens);
            return withEventSummaries(bundle, summaries);
        }

        // Over budget — delegate to EventSummarizer agent
        log.info("EventHistoryPlanner: {} events ({} tokens) exceed remaining budget of {} — summarising",
                events.size(), estimatedTokens, remainingTokens);
        String summary = summarise(combined, req);
        return withHistorySummary(bundle, summary);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<String> formatEvents(List<SessionEventEntity> events) {
        List<String> lines = new ArrayList<>();
        for (SessionEventEntity e : events) {
            String author = e.getAuthor() != null ? e.getAuthor() : "unknown";
            String role = e.getRole() != null ? e.getRole() : "?";
            String text = e.getTextContent() != null
                    ? (e.getTextContent().length() > 200
                            ? e.getTextContent().substring(0, 200) + "\u2026"
                            : e.getTextContent())
                    : "(no text)";
            lines.add(String.format("[%s | %s | %s] %s", e.getOccurredAt(), role, author, text));
        }
        return lines;
    }

    /**
     * Invoke the EventSummarizer agent synchronously (blocks up to 30 s).
     * Returns a best-effort summary or a fallback message if the agent fails.
     */
    private String summarise(String rawHistory, DecisionRequest req) {
        try {
            Content prompt = Content.fromParts(
                    Part.fromText("Summarise the following session event history concisely (≤300 tokens):"),
                    Part.fromText(rawHistory));

            AgentContext ctx = null; // no context needed for summariser
            Flowable<com.google.adk.events.Event> flow = agentOrchestrator.createTaskFlowable(
                    agentRegistry.get(AgentRegistry.EVENT_SUMMARIZER_AGENT_ID),
                    UUID.randomUUID().toString(), prompt, ctx);

            StringBuilder sb = new StringBuilder();
            flow.blockingForEach(event -> {
                if (event.content().isPresent()) {
                    event.content().get().parts().ifPresent(parts ->
                            parts.stream()
                                    .filter(p -> p.text().isPresent())
                                    .forEach(p -> sb.append(p.text().get())));
                }
            });

            String result = sb.toString().trim();
            return result.isBlank() ? "(summarisation returned empty)" : result;

        } catch (Exception e) {
            log.warn("EventHistoryPlanner: summarisation failed — {}", e.getMessage());
            return "(event history summary unavailable)";
        }
    }

    private ContextBundle withEventSummaries(ContextBundle b, List<String> summaries) {
        return new ContextBundle(b.facts(), b.pages(), b.toolReceipts(), b.provenance(),
                b.tokenEstimate(), summaries, null);
    }

    private ContextBundle withHistorySummary(ContextBundle b, String summary) {
        return new ContextBundle(b.facts(), b.pages(), b.toolReceipts(), b.provenance(),
                b.tokenEstimate(), List.of(), summary);
    }
}

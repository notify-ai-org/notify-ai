package com.example.agent.planner;

import com.example.agent.enums.DecisionType;
import com.example.agent.enums.PageType;
import com.example.agent.interfaces.*;
import com.example.agent.records.*;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DefaultRetrievalPlanner implements RetrievalPlanner {

    private final FactStore factStore;
    private final MemoryAssembler memoryAssembler;
    private final TokenEstimator tokenEstimator;

    public DefaultRetrievalPlanner(FactStore factStore, MemoryAssembler memoryAssembler,
            TokenEstimator tokenEstimator) {
        this.factStore = factStore;
        this.memoryAssembler = memoryAssembler;
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public ContextBundle plan(DecisionRequest req) {
        Instant now = Instant.now();
        int budget = req.tokenBudget();

        // Reserve some room for PromptAssembler instruction/schema and model output
        // buffer
        int reservedForPromptOverhead = Math.min(350, budget / 3);
        int reservedForModelBuffer = Math.min(250, budget / 4);
        budget = Math.max(0, budget - reservedForPromptOverhead - reservedForModelBuffer);

        List<Fact> facts = factStore.fetchFacts(req.decisionType(), now);
        budget -= estimateFactsTokens(facts);

        // Build decision-aware retrieval queries
        List<RetrievalQuery> queries = buildQueries(req);

        // Vector search candidates
        Instant since = now.minus(Duration.ofDays(req.timeWindowDays()));
        List<VectorCandidate> candidates = new ArrayList<>();
        for (RetrievalQuery q : queries) {
            candidates.addAll(memoryAssembler.search(
                    q.queryText(),
                    Set.of(PageType.SEMANTIC, PageType.EPISODIC),
                    since,
                    q.k()));
        }

        // Deduplicate by pageId, keep max similarity
        Map<String, VectorCandidate> bestByPageId = new HashMap<>();
        for (VectorCandidate c : candidates) {
            bestByPageId.merge(c.page().pageId(), c, (a, b) -> b.similarity() > a.similarity() ? b : a);
        }
        List<VectorCandidate> unique = new ArrayList<>(bestByPageId.values());

        // Rerank
        List<ScoredPage> scored = rerank(unique, req, now);

        // Select within remaining budget using ROI
        List<MemoryPage> selected = new ArrayList<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        List<String> dropped = new ArrayList<>();

        // Always keep procedural + facts even if budget gets tight
        List<MemoryPage> base = new ArrayList<>();

        // Partition semantic first, episodic later
        List<ScoredPage> semantic = scored.stream().filter(sp -> sp.page.pageType() == PageType.SEMANTIC).toList();
        List<ScoredPage> episodic = scored.stream().filter(sp -> sp.page.pageType() == PageType.EPISODIC).toList();

        budget = selectPagesWithinBudget(semantic, budget, selected, reasons, dropped);
        budget = selectPagesWithinBudget(episodic, budget, selected, reasons, dropped);

        // Tool receipts are typically built from the event pipeline; placeholder here
        List<ToolReceipt> toolReceipts = List.of(); // inject from your execution context if needed

        // Final pages = procedural + selected
        List<MemoryPage> pages = new ArrayList<>(base);
        pages.addAll(selected);

        int totalTokens = req.tokenBudget() - Math.max(0, budget) - reservedForModelBuffer; // approximate
        Provenance provenance = new Provenance(queries, reasons, dropped);
        return new ContextBundle(facts, pages, toolReceipts, provenance, Math.max(0, totalTokens));
    }

    private int selectPagesWithinBudget(
            List<ScoredPage> scored,
            int budget,
            List<MemoryPage> selected,
            Map<String, String> reasons,
            List<String> dropped) {
        // Sort by ROI = score / tokenCost
        List<ScoredPage> sorted = new ArrayList<>(scored);
        sorted.sort(Comparator.<ScoredPage>comparingDouble(sp -> -sp.roi));

        for (ScoredPage sp : sorted) {
            int cost = tokenEstimator.estimateTokens(sp.page.summary());
            if (cost <= budget) {
                selected.add(sp.page);
                budget -= cost;
                reasons.put(sp.page.pageId(), sp.reason);
            } else {
                // Optional: if too big, attempt “minify”
                MemoryPage minified = minify(sp.page);
                int minCost = tokenEstimator.estimateTokens(minified.summary());
                if (minCost <= budget && minCost < cost) {
                    selected.add(minified);
                    budget -= minCost;
                    reasons.put(minified.pageId(), sp.reason + " | minified");
                } else {
                    dropped.add(sp.page.pageId());
                }
            }
            if (budget <= 0)
                break;
        }
        return Math.max(0, budget);
    }

    private MemoryPage minify(MemoryPage page) {
        // Deterministic minify: shorten to 1-2 lines, drop fluff.
        // Avoid LLM here. Keep: what happened + outcome + timestamp hint.
        String s = page.summary();
        String trimmed = s.length() > 240 ? s.substring(0, 240) + "…" : s;
        return new MemoryPage(
                page.pageId(),
                page.pageType(),
                trimmed,
                page.timestamp(),
                page.importance(),
                page.confidence(),
                page.createdAt(),
                page.updatedAt(),
                page.tags(),
                page.scope(),
                page.rawRef());
    }

    private int estimateFactsTokens(List<Fact> facts) {
        int sum = 0;
        for (Fact f : facts)
            sum += tokenEstimator.estimateTokens(f.sentence());
        return sum;
    }

    private List<RetrievalQuery> buildQueries(DecisionRequest req) {
        List<EntityRef> scope = req.entities();
        return switch (req.decisionType()) {
            case CHANNEL_FALLBACK -> List.of(
                    new RetrievalQuery("user channel preference and engagement patterns",
                            Set.of("preference", "engagement", "channel"), scope, PageType.SEMANTIC, 25),
                    new RetrievalQuery("recent delivery failures and suppression reasons",
                            Set.of("failure", "suppression", "retry"), scope, PageType.EPISODIC, 25),
                    new RetrievalQuery("tenant provider reliability incidents",
                            Set.of("incident", "provider", "outage"), scope,
                            PageType.SEMANTIC, 25),
                    new RetrievalQuery("successful fallback patterns after similar failures",
                            Set.of("fallback", "success"), scope, PageType.SEMANTIC, 25));
            case SCHEDULE -> List.of(
                    new RetrievalQuery("preferred send windows and do-not-disturb behavior",
                            Set.of("schedule", "dnd", "window"), scope, PageType.SEMANTIC, 25),
                    new RetrievalQuery("recent suppressions and reschedules", Set.of("suppression", "reschedule"),
                            scope, PageType.EPISODIC, 25));
            case SUPPRESS -> List.of(
                    new RetrievalQuery("suppression reasons and complaints history",
                            Set.of("complaint", "optout", "suppression"), scope, PageType.EPISODIC, 25),
                    new RetrievalQuery("policy rules for suppression and compliance", Set.of("policy", "compliance"),
                            scope, PageType.PROCEDURAL, 15));
            case TEMPLATE_PICK -> List.of(
                    new RetrievalQuery("template performance and engagement by channel",
                            Set.of("template", "engagement"), scope, PageType.SEMANTIC, 25),
                    new RetrievalQuery("recent template failures and rendering issues", Set.of("template", "failure"),
                            scope, PageType.EPISODIC, 25));
            case ESCALATE -> List.of(
                    new RetrievalQuery("escalation rules and severity thresholds", Set.of("escalation", "severity"),
                            scope, PageType.PROCEDURAL, 15),
                    new RetrievalQuery("similar escalations and outcomes", Set.of("escalation", "outcome"), scope,
                            PageType.EPISODIC, 25));
        };
    }

    private record ScoredPage(MemoryPage page, double score, double roi, String reason) {
    }

    private List<ScoredPage> rerank(List<VectorCandidate> candidates, DecisionRequest req, Instant now) {
        return candidates.stream()
                .map(c -> {
                    MemoryPage p = c.page();
                    double sim = c.similarity();

                    double recency = recencyScore(p.timestamp(), now);
                    double decisionMatch = decisionMatchScore(p.tags(), req.decisionType());
                    double importance = clamp01(p.importance());
                    double scopeMatch = scopeMatchScore(p.scope(), req.entities());
                    double redundancyPenalty = 0.0; // handled via dedupe; can extend via near-dup hash

                    double score = 0.45 * sim +
                            0.20 * recency +
                            0.20 * decisionMatch +
                            0.10 * importance +
                            0.05 * scopeMatch -
                            0.30 * redundancyPenalty;

                    int tokenCost = Math.max(1, tokenEstimator.estimateTokens(p.summary()));
                    double roi = score / tokenCost;

                    String reason = "sim=" + round2(sim) +
                            ", recency=" + round2(recency) +
                            ", decisionMatch=" + round2(decisionMatch) +
                            ", importance=" + round2(importance) +
                            ", scopeMatch=" + round2(scopeMatch);

                    return new ScoredPage(p, score, roi, reason);
                })
                .sorted(Comparator.comparingDouble((ScoredPage sp) -> -sp.score))
                .toList();
    }

    private double recencyScore(Instant ts, Instant now) {
        long hours = Math.max(0, Duration.between(ts, now).toHours());
        // piecewise: very recent gets strong boost, older fades
        if (hours <= 6)
            return 1.0;
        if (hours <= 24)
            return 0.8;
        if (hours <= 72)
            return 0.6;
        if (hours <= 168)
            return 0.4;
        return 0.2;
    }

    private double decisionMatchScore(Set<String> tags, DecisionType dt) {
        if (tags == null || tags.isEmpty())
            return 0.3;
        Set<String> wanted = switch (dt) {
            case CHANNEL_FALLBACK ->
                Set.of("fallback", "failure", "provider", "channel", "retry", "engagement", "preference");
            case SCHEDULE -> Set.of("schedule", "dnd", "window", "digest", "timezone");
            case SUPPRESS -> Set.of("optout", "suppression", "complaint", "compliance");
            case TEMPLATE_PICK -> Set.of("template", "render", "click", "engagement");
            case ESCALATE -> Set.of("escalation", "severity", "pager", "oncall", "incident");
        };
        long hits = tags.stream().filter(wanted::contains).count();
        return clamp01(0.2 + (hits / (double) Math.max(1, wanted.size())) * 1.2);
    }

    private double scopeMatchScore(List<EntityRef> pageScope, List<EntityRef> reqScope) {
        if (pageScope == null || pageScope.isEmpty())
            return 0.2;
        Set<String> req = reqScope.stream().map(e -> e.type() + ":" + e.id()).collect(Collectors.toSet());
        long hits = pageScope.stream().map(e -> e.type() + ":" + e.id()).filter(req::contains).count();
        return clamp01(0.2 + (hits / (double) Math.max(1, pageScope.size())) * 1.2);
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

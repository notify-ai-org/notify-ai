package com.example.agent.assembler;

import com.example.agent.enums.PageType;
import com.example.agent.interfaces.*;
import com.example.agent.records.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DefaultPromptAssembler implements PromptAssembler {

    private final TokenEstimator tokenEstimator;

    public DefaultPromptAssembler(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public PromptPackage assemble(DecisionRequest req, ContextBundle bundle) {
        // Section caps inside the provided budget (keep strict)
        SectionCaps caps = SectionCaps.forBudget(req.tokenBudget());

        String system = buildSystem(req);
        String user = buildUser(req, bundle, caps);

        // Final enforcement: if over budget, prune lowest priority memory pages
        PromptPackage pkg = new PromptPackage(system, user);
        return enforceBudget(req.tokenBudget(), pkg, bundle, caps);
    }

    private String buildSystem(DecisionRequest req) {
        return """
                You are a decision agent for an event-driven Notification Engine.
                Use ONLY the provided facts, rules, and memory. Do not invent missing data.
                If facts conflict with memory: Facts > Procedural Rules > Semantic Memory > Episodic Memory.
                Keep outputs short and deterministic. Output MUST follow the provided JSON schema exactly.
                """;
    }

    private String buildUser(DecisionRequest req, ContextBundle bundle, SectionCaps caps) {
        StringBuilder sb = new StringBuilder();

        // 1) Task
        sb.append("## Task\n");
        sb.append("- decisionType: ").append(req.decisionType()).append("\n");

        sb.append("- entities: ").append(req.entities()).append("\n");
        sb.append("- event: ").append(req.eventRef()).append("\n\n");

        // 2) Facts
        sb.append("## Deterministic Facts (use these first)\n");
        sb.append(limitToCap(formatFacts(bundle.facts()), caps.factsTokens)).append("\n\n");

        // 3) Procedural rules
        sb.append("## Procedural Rules\n");
        sb.append(limitToCap(formatPages(bundle.pages(), PageType.PROCEDURAL), caps.proceduralTokens)).append("\n\n");

        // 4) Semantic memory
        sb.append("## Semantic Memory\n");
        sb.append(limitToCap(formatPages(bundle.pages(), PageType.SEMANTIC), caps.semanticTokens)).append("\n\n");

        // 5) Episodic memory
        sb.append("## Episodic Memory\n");
        sb.append(limitToCap(formatPages(bundle.pages(), PageType.EPISODIC), caps.episodicTokens)).append("\n\n");

        // 6) Tool receipts (already stripped)
        sb.append("## Tool Receipts (summaries only)\n");
        sb.append(limitToCap(formatToolReceipts(bundle.toolReceipts()), caps.toolTokens)).append("\n\n");

        return sb.toString();
    }

    private String formatFacts(List<Fact> facts) {
        if (facts == null || facts.isEmpty())
            return "- (none)\n";
        StringBuilder sb = new StringBuilder();
        for (Fact f : facts) {
            sb.append("- [").append(f.factId()).append("] ")
                    .append(f.sentence())
                    .append(" (confidence=").append(round2(f.confidence()))
                    .append(", observedAt=").append(f.observedAt())
                    .append(")\n");
        }
        return sb.toString();
    }

    private String formatPages(List<MemoryPage> pages, PageType type) {
        List<MemoryPage> filtered = pages == null ? List.of()
                : pages.stream().filter(p -> p.pageType() == type).toList();

        if (filtered.isEmpty())
            return "- (none)\n";

        StringBuilder sb = new StringBuilder();
        for (MemoryPage p : filtered) {
            sb.append("- [").append(p.pageId()).append("] ")
                    .append(p.summary()).append("\n")
                    .append("  - ts: ").append(p.timestamp()).append("\n")
                    .append("  - tags: ").append(p.tags()).append("\n")
                    .append("  - confidence: ").append(round2(p.confidence()))
                    .append(", importance: ").append(round2(p.importance())).append("\n");
        }
        return sb.toString();
    }

    private String formatToolReceipts(List<ToolReceipt> receipts) {
        if (receipts == null || receipts.isEmpty())
            return "- (none)\n";
        StringBuilder sb = new StringBuilder();
        for (ToolReceipt r : receipts) {
            sb.append("- ").append(r.toolName())
                    .append(" cid=").append(r.correlationId())
                    .append(" status=").append(r.status())
                    .append(" keyFields=").append(r.keyFields())
                    .append("\n");
        }
        return sb.toString();
    }

    private String limitToCap(String text, int capTokens) {
        if (capTokens <= 0)
            return "";
        int tokens = tokenEstimator.estimateTokens(text);
        if (tokens <= capTokens)
            return text;

        // Deterministic truncation: keep first N chars proportional to cap
        int approxChars = capTokens * 4;
        if (text.length() <= approxChars)
            return text;
        return text.substring(0, Math.max(0, approxChars)) + "\n- …(truncated for token budget)\n";
    }

    private PromptPackage enforceBudget(int totalBudget, PromptPackage pkg, ContextBundle bundle, SectionCaps caps) {
        int est = tokenEstimator.estimateTokens(pkg.systemPrompt()) + tokenEstimator.estimateTokens(pkg.userPrompt());
        if (est <= totalBudget)
            return pkg;

        // If over budget: prune memory (episodic first, then semantic), keep facts +
        // procedural
        List<MemoryPage> pages = new ArrayList<>(bundle.pages());

        pages.sort((a, b) -> {
            // drop EPISODIC first, then SEMANTIC, keep PROCEDURAL
            int pa = priority(a.pageType());
            int pb = priority(b.pageType());
            if (pa != pb)
                return Integer.compare(pb, pa); // higher = drop sooner
            // within same type: drop lowest importance first
            return Double.compare(a.importance(), b.importance());
        });

        List<MemoryPage> kept = new ArrayList<>();
        for (MemoryPage p : pages) {
            if (p.pageType() == PageType.PROCEDURAL)
                kept.add(p);
        }

        // then add semantic until cap, then episodic until cap
        for (PageType t : List.of(PageType.SEMANTIC, PageType.EPISODIC)) {
            for (MemoryPage p : pages) {
                if (p.pageType() != t)
                    continue;
                kept.add(p);
                ContextBundle tmp = new ContextBundle(bundle.facts(), kept, bundle.toolReceipts(), bundle.provenance(),
                        bundle.tokenEstimate());
                String user = buildUserDummyForBudget(tmp, caps); // avoid recursion using simplified build
                int est2 = tokenEstimator.estimateTokens(pkg.systemPrompt()) + tokenEstimator.estimateTokens(user);
                if (est2 > totalBudget) {
                    kept.remove(kept.size() - 1);
                }
            }
        }

        ContextBundle shrunk = new ContextBundle(bundle.facts(), kept, bundle.toolReceipts(), bundle.provenance(),
                bundle.tokenEstimate());
        String rebuiltUser = buildUserDummyForBudget(shrunk, caps);
        return new PromptPackage(pkg.systemPrompt(), rebuiltUser);
    }

    private String buildUserDummyForBudget(ContextBundle bundle, SectionCaps caps) {
        // Minimal rebuild for budget enforcement. (In real implementation, call
        // buildUser with request too.)
        StringBuilder sb = new StringBuilder();
        sb.append("## Deterministic Facts\n")
                .append(limitToCap(formatFacts(bundle.facts()), caps.factsTokens)).append("\n\n");
        sb.append("## Procedural Rules\n")
                .append(limitToCap(formatPages(bundle.pages(), PageType.PROCEDURAL), caps.proceduralTokens))
                .append("\n\n");
        sb.append("## Semantic Memory\n")
                .append(limitToCap(formatPages(bundle.pages(), PageType.SEMANTIC), caps.semanticTokens)).append("\n\n");
        sb.append("## Episodic Memory\n")
                .append(limitToCap(formatPages(bundle.pages(), PageType.EPISODIC), caps.episodicTokens)).append("\n\n");
        sb.append("## Tool Receipts\n")
                .append(limitToCap(formatToolReceipts(bundle.toolReceipts()), caps.toolTokens)).append("\n\n");
        return sb.toString();
    }

    private int priority(PageType t) {
        // higher = drop sooner
        return switch (t) {
            case EPISODIC -> 3;
            case SEMANTIC -> 2;
            case PROCEDURAL -> 0;
        };
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static class SectionCaps {
        final int factsTokens;
        final int proceduralTokens;
        final int semanticTokens;
        final int episodicTokens;
        final int toolTokens;

        private SectionCaps(int facts, int procedural, int semantic, int episodic, int tool) {
            this.factsTokens = facts;
            this.proceduralTokens = procedural;
            this.semanticTokens = semantic;
            this.episodicTokens = episodic;
            this.toolTokens = tool;
        }

        static SectionCaps forBudget(int total) {
            // Conservative caps: keep “reasonable slices”
            int facts = Math.min(350, total / 6);
            int procedural = Math.min(280, total / 7);
            int semantic = Math.min(550, total / 4);
            int episodic = Math.min(280, total / 7);
            int tool = Math.min(180, total / 9);
            return new SectionCaps(facts, procedural, semantic, episodic, tool);
        }
    }
}

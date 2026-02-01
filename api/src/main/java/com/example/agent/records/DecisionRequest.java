package com.example.agent.records;

import com.example.agent.enums.DecisionType;
import java.util.List;

public record DecisionRequest(
        String tenantId,
        DecisionType decisionType,
        List<EntityRef> entities,
        EventRef eventRef,
        int timeWindowDays,
        int tokenBudget,
        int latencyBudgetMs,
        String locale,
        String timezone
) {
}

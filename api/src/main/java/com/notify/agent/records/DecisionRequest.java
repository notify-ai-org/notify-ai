package com.notify.agent.records;

import com.notify.agent.enums.DecisionType;
import java.util.List;

public record DecisionRequest(
                DecisionType decisionType,
                List<EntityRef> entities,
                EventRef eventRef,
                int timeWindowDays,
                int tokenBudget,
                int latencyBudgetMs,
                String locale,
                String timezone,
                String sessionId) {
}

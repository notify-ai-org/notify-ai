package com.notify.agent.interfaces;

import com.notify.agent.records.Fact;
import com.notify.agent.enums.DecisionType;

import java.time.Instant;
import java.util.List;

public interface FactStore {
    List<Fact> fetchFacts(DecisionType decisionType, Instant now);
}

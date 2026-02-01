package com.example.agent.interfaces;

import com.example.agent.records.EntityRef;
import com.example.agent.records.Fact;
import com.example.agent.enums.DecisionType;

import java.time.Instant;
import java.util.List;

public interface FactStore {
    List<Fact> fetchFacts(String tenantId, List<EntityRef> scope, DecisionType decisionType, Instant now);
}


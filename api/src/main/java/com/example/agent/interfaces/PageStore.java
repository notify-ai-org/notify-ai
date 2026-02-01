package com.example.agent.interfaces;

import com.example.agent.records.MemoryPage;
import com.example.agent.enums.DecisionType;

import java.util.List;

public interface PageStore {
    List<MemoryPage> fetchProcedural(String tenantId, DecisionType decisionType, int limit);
}

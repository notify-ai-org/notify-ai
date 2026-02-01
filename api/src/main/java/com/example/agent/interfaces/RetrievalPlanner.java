package com.example.agent.interfaces;

import com.example.agent.records.ContextBundle;
import com.example.agent.records.DecisionRequest;

public interface RetrievalPlanner {
    ContextBundle plan(DecisionRequest request);
}

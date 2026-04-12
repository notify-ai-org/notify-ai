package com.notify.agent.interfaces;

import com.notify.agent.records.ContextBundle;
import com.notify.agent.records.DecisionRequest;

public interface RetrievalPlanner {
    ContextBundle plan(DecisionRequest request);
}

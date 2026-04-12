package com.notify.agent.interfaces;

import com.notify.agent.records.ContextBundle;
import com.notify.agent.records.DecisionRequest;
import com.notify.agent.records.PromptPackage;

public interface PromptAssembler {
    PromptPackage assemble(DecisionRequest request, ContextBundle bundle);
}

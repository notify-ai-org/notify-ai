package com.example.agent.interfaces;

import com.example.agent.records.ContextBundle;
import com.example.agent.records.DecisionRequest;
import com.example.agent.records.PromptPackage;

public interface PromptAssembler {
    PromptPackage assemble(DecisionRequest request, ContextBundle bundle);
}

package com.example.agent.models;

import com.example.agent.interfaces.TokenEstimator;
import org.springframework.stereotype.Component;

@Component
public class HeuristicTokenEstimator implements TokenEstimator {
    // Roughly: 1 token ~ 4 chars in English-ish text (varies by model/language)
    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int chars = text.length();
        return Math.max(1, chars / 4);
    }
}

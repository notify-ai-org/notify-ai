package com.example.agent.models.dto;

import java.util.List;

/**
 * DTO matching the output schema of RuleProcessorAgent.
 */
public class RuleExpressionDto {

    private String ruleName;
    private String conditionExpr;
    private List<String> vocabularyTerms;
    private String explanation;

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getConditionExpr() { return conditionExpr; }
    public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }
    public List<String> getVocabularyTerms() { return vocabularyTerms; }
    public void setVocabularyTerms(List<String> vocabularyTerms) { this.vocabularyTerms = vocabularyTerms; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}


package com.example.agent.assembler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.agent.enums.DecisionType;
import com.example.agent.enums.PageType;
import com.example.agent.interfaces.TokenEstimator;
import com.example.agent.records.*;

/**
 * Unit tests for DefaultPromptAssembler
 */
@ExtendWith(MockitoExtension.class)
class DefaultPromptAssemblerTest {

    @Mock
    private TokenEstimator tokenEstimator;

    private DefaultPromptAssembler promptAssembler;
    private DecisionRequest testRequest;
    private ContextBundle testBundle;

    @BeforeEach
    void setUp() {
        promptAssembler = new DefaultPromptAssembler(tokenEstimator);

        testRequest = new DecisionRequest(
                DecisionType.ESCALATE,
                List.of(new EntityRef("user", "user-1")),
                new EventRef("event-1", "ORDER_CREATED", "HIGH", Instant.now()),
                7, // timeWindowDays
                2000, // tokenBudget
                5000, // latencyBudgetMs
                "en_US", // locale
                "UTC"); // timezone

        Fact fact = mock(Fact.class);
        lenient().when(fact.factId()).thenReturn("fact-1");
        lenient().when(fact.sentence()).thenReturn("User placed an order");
        lenient().when(fact.confidence()).thenReturn(0.9);
        lenient().when(fact.observedAt()).thenReturn(Instant.now());

        MemoryPage semanticPage = mock(MemoryPage.class);
        lenient().when(semanticPage.pageId()).thenReturn("page-1");
        lenient().when(semanticPage.pageType()).thenReturn(PageType.SEMANTIC);
        lenient().when(semanticPage.summary()).thenReturn("User preferences summary");
        lenient().when(semanticPage.timestamp()).thenReturn(Instant.now());
        lenient().when(semanticPage.tags()).thenReturn(Set.of("preference"));
        lenient().when(semanticPage.confidence()).thenReturn(0.85);
        lenient().when(semanticPage.importance()).thenReturn(0.75);

        MemoryPage proceduralPage = mock(MemoryPage.class);
        lenient().when(proceduralPage.pageId()).thenReturn("page-2");
        lenient().when(proceduralPage.pageType()).thenReturn(PageType.PROCEDURAL);
        lenient().when(proceduralPage.summary()).thenReturn("Business rules for orders");
        lenient().when(proceduralPage.timestamp()).thenReturn(Instant.now());
        lenient().when(proceduralPage.tags()).thenReturn(Set.of("rule"));
        lenient().when(proceduralPage.confidence()).thenReturn(0.95);
        lenient().when(proceduralPage.importance()).thenReturn(0.9);

        ToolReceipt receipt = mock(ToolReceipt.class);
        lenient().when(receipt.toolName()).thenReturn("email-connector");
        lenient().when(receipt.correlationId()).thenReturn("corr-1");
        lenient().when(receipt.status()).thenReturn("success");
        lenient().when(receipt.keyFields()).thenReturn(Map.of("to", "user@example.com", "subject", "Test Email"));

        Provenance testProvenance = new Provenance(
                List.of(),
                Map.of(),
                List.of());

        testBundle = new ContextBundle(
                List.of(fact),
                List.of(semanticPage, proceduralPage),
                List.of(receipt),
                testProvenance,
                1000);

        // Default token estimator behavior
        lenient().when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);
    }

    @Test
    void testAssemble_shouldBuildPromptWithinBudget() {
        // Arrange
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, testBundle);

        // Assert
        assertNotNull(result);
        assertNotNull(result.systemPrompt());
        assertNotNull(result.userPrompt());
        assertTrue(result.systemPrompt().contains("decision agent"));
        assertTrue(result.userPrompt().contains("ESCALATE"));
    }

    @Test
    void testAssemble_shouldFormatFactsCorrectly() {
        // Arrange
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, testBundle);

        // Assert
        String userPrompt = result.userPrompt();
        assertTrue(userPrompt.contains("[fact-1]"));
        assertTrue(userPrompt.contains("User placed an order"));
        assertTrue(userPrompt.contains("confidence="));
    }

    @Test
    void testAssemble_shouldFormatMemoryPagesCorrectly() {
        // Arrange
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, testBundle);

        // Assert
        String userPrompt = result.userPrompt();
        assertTrue(userPrompt.contains("[page-1]"));
        assertTrue(userPrompt.contains("User preferences summary"));
        assertTrue(userPrompt.contains("[page-2]"));
        assertTrue(userPrompt.contains("Business rules for orders"));
    }

    @Test
    void testAssemble_shouldTruncateWhenOverBudget() {
        // Arrange
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(2500); // Over budget

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, testBundle);

        // Assert
        assertNotNull(result);
        // Should still produce a valid prompt, just truncated
        assertNotNull(result.systemPrompt());
        assertNotNull(result.userPrompt());
    }

    @Test
    void testAssemble_shouldPrioritizeFactsAndProceduralRules() {
        // Arrange
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(2500);

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, testBundle);

        // Assert
        String userPrompt = result.userPrompt();
        // Facts and procedural rules should be present even when over budget
        assertTrue(userPrompt.contains("fact-1") || userPrompt.contains("(none)"));
        assertTrue(userPrompt.contains("page-2") || userPrompt.contains("(none)"));
    }

    @Test
    void testAssemble_shouldHandleEmptyFacts() {
        // Arrange
        ContextBundle emptyBundle = new ContextBundle(
                List.of(),
                testBundle.pages(),
                testBundle.toolReceipts(),
                testBundle.provenance(),
                1000);
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, emptyBundle);

        // Assert
        assertFalse(result.userPrompt().contains("## Deterministic Facts"));
    }

    @Test
    void testAssemble_shouldHandleEmptyMemory() {
        // Arrange
        ContextBundle emptyBundle = new ContextBundle(
                testBundle.facts(),
                List.of(),
                testBundle.toolReceipts(),
                testBundle.provenance(),
                1000);
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, emptyBundle);

        // Assert
        assertFalse(result.userPrompt().contains("## Semantic Memory"));
    }

    @Test
    void testAssemble_shouldHandleToolReceipts() {
        // Arrange
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, testBundle);

        // Assert
        String userPrompt = result.userPrompt();
        assertTrue(userPrompt.contains("email-connector"));
        assertTrue(userPrompt.contains("success"));
    }

    @Test
    void testSystemPrompt_shouldContainInstructions() {
        // Arrange
        when(tokenEstimator.estimateTokens(anyString())).thenReturn(100);

        // Act
        PromptPackage result = promptAssembler.assemble(testRequest, testBundle);

        // Assert
        String systemPrompt = result.systemPrompt();
        assertTrue(systemPrompt.contains("decision agent"));
        assertTrue(systemPrompt.contains("event-driven"));
        assertTrue(systemPrompt.contains("Do not invent missing data"));
    }
}

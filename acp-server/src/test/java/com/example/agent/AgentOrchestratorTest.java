package com.example.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.adk.agents.LlmAgent;
import com.google.genai.types.*;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for AgentOrchestrator
 */
@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    private AgentOrchestrator orchestrator;
    private LlmAgent mockAgent;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator();
        mockAgent = mock(LlmAgent.class);
    }

    @Test
    void testRegisterAgent_shouldAssignUniqueId() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        String id1 = orchestrator.registerAgent(mockAgent);
        String id2 = orchestrator.registerAgent(mockAgent);

        // Assert
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "Each registration should get a unique ID");
    }

    @Test
    void testRegisterAgent_shouldStoreAgent() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        String id = orchestrator.registerAgent(mockAgent);

        // Assert
        LlmAgent retrieved = orchestrator.getAgent(id);
        assertNotNull(retrieved);
        assertEquals(mockAgent, retrieved);
    }

    @Test
    void testGetAgent_shouldReturnNullForNonExistentId() {
        // Act
        LlmAgent result = orchestrator.getAgent("non-existent-id");

        // Assert
        assertNull(result);
    }

    @Test
    void testExecuteAgent_shouldInvokeAgent() {
        // Arrange
        String agentId = setupMockAgent();
        Content input = Content.fromParts(Part.fromText("{\"key\": \"value\"}"));
        Content expectedOutput = Content.fromParts(Part.fromText("{\"result\": \"success\"}"));

        when(mockAgent.executeTaskWithAgent(any(Content.class))).thenReturn(Mono.just(expectedOutput));

        // Act & Assert
        StepVerifier.create(orchestrator.executeAgent(agentId, input, Map.of()))
                .expectNext(expectedOutput)
                .verifyComplete();
    }

    @Test
    void testExecuteAgent_shouldHandleAgentFailure() {
        // Arrange
        String agentId = setupMockAgent();
        Content input = Content.fromParts(Part.fromText("{\"key\": \"value\"}"));

        when(mockAgent.execute(any(Content.class)))
                .thenReturn(Mono.error(new RuntimeException("Agent execution failed")));

        // Act & Assert
        StepVerifier.create(orchestrator.executeAgent(agentId, input, Map.of()))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage().contains("Agent execution failed"))
                .verify();
    }

    @Test
    void testExecuteAgent_withNonExistentAgent_shouldFail() {
        // Arrange
        Content input = Content.fromParts(Part.fromText("{\"key\": \"value\"}"));

        // Act & Assert
        StepVerifier.create(orchestrator.executeAgent("non-existent-id", input, Map.of()))
                .expectError()
                .verify();
    }

    @Test
    void testExecuteAgent_withNullInput_shouldHandleGracefully() {
        // Arrange
        String agentId = setupMockAgent();

        when(mockAgent.execute(any(Content.class)))
                .thenReturn(Mono.just(Content.fromParts(Part.fromText("{}"))));

        // Act & Assert
        StepVerifier.create(orchestrator.executeAgent(agentId, null, Map.of()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void testRegisterAgent_withSameAgent_shouldCreateSeparateRegistrations() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        String id1 = orchestrator.registerAgent(mockAgent);
        String id2 = orchestrator.registerAgent(mockAgent);

        // Assert
        LlmAgent agent1 = orchestrator.getAgent(id1);
        LlmAgent agent2 = orchestrator.getAgent(id2);

        assertNotNull(agent1);
        assertNotNull(agent2);
        assertEquals(agent1, agent2); // Same agent instance
        assertNotEquals(id1, id2); // Different IDs
    }

    @Test
    void testExecuteAgent_shouldPassMetadataToAgent() {
        // Arrange
        String agentId = setupMockAgent();
        Content input = Content.fromParts(Part.fromText("{\"key\": \"value\"}"));
        Content expectedOutput = Content.fromParts(Part.fromText("{\"result\": \"success\"}"));
        Map<String, Object> metadata = Map.of("tenantId", "tenant-1", "correlationId", "corr-1");

        when(mockAgent.execute(any(Content.class))).thenReturn(Mono.just(expectedOutput));

        // Act & Assert
        StepVerifier.create(orchestrator.executeAgent(agentId, input, metadata))
                .expectNext(expectedOutput)
                .verifyComplete();
    }

    // Helper methods

    private String setupMockAgent() {
        when(mockAgent.name()).thenReturn("Test Agent");
        String agentId = orchestrator.registerAgent(mockAgent);
        return agentId;
    }
}

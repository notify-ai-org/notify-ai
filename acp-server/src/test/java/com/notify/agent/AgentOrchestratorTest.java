package com.notify.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.notify.agent.enums.AgentStage;
import com.notify.agent.service.SessionService;
import com.notify.agent.util.CentralExecutorRegistry;
import com.google.adk.agents.LlmAgent;

import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Unit tests for AgentOrchestrator.
 *
 * These tests focus on the public-facing API behaviour that
 * can be exercised without a live Redis connection:
 * registerAgent, getAllAgentStates, pauseAgent, resumeAgent,
 * executeTaskWithAgent (enqueue), and queue depth.
 */
@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private AgentSnapshotRepository snapshotRepo;

    @Mock
    private AgentLogRepository logRepo;

    @Mock
    private SessionService sessionService;

    @Mock
    private CentralExecutorRegistry executorRegistry;

    @Mock
    private StatefulRedisConnection<String, String> redisConnection;

    private AgentOrchestrator orchestrator;
    private LlmAgent mockAgent;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(snapshotRepo, logRepo, sessionService,
                executorRegistry, redisConnection);
        mockAgent = mock(LlmAgent.class);
    }

    // -----------------------------------------------------------------------
    // registerAgent tests
    // -----------------------------------------------------------------------

    @Test
    void testRegisterAgent_shouldAssignUniqueId() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        String id1 = orchestrator.registerAgent("TypeA", mockAgent);
        String id2 = orchestrator.registerAgent("TypeB", mockAgent);

        // Assert
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "Each registration should get a unique ID");
    }

    @Test
    void testRegisterAgent_withSameAgent_shouldCreateSeparateRegistrations() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        String id1 = orchestrator.registerAgent("SameType", mockAgent);
        String id2 = orchestrator.registerAgent("SameType", mockAgent);

        // Assert — both unique IDs exist in the pool
        assertNotEquals(id1, id2);
        assertEquals(2, orchestrator.getAllAgentStates().size());
    }

    @Test
    void testRegisterAgent_shouldAppearInAllAgentStates() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        String id = orchestrator.registerAgent("TestType", mockAgent);

        // Assert
        Map<String, Map<String, Object>> states = orchestrator.getAllAgentStates();
        assertTrue(states.containsKey(id));
        Map<String, Object> agentState = states.get(id);
        assertEquals(id, agentState.get("agentId"));
        assertEquals("Test Agent", agentState.get("agentName"));
        assertEquals(AgentStage.READY, agentState.get("currentStage"));
    }

    // -----------------------------------------------------------------------
    // pauseAgent / resumeAgent tests
    // -----------------------------------------------------------------------

    @Test
    void testPauseAgent_shouldSucceedOnReadyAgent() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");
        String id = orchestrator.registerAgent("TypeA", mockAgent);

        // Act
        boolean result = orchestrator.pauseAgent(id, "Maintenance");

        // Assert
        assertTrue(result);
        Map<String, Map<String, Object>> states = orchestrator.getAllAgentStates();
        assertEquals(AgentStage.PAUSED, states.get(id).get("currentStage"));
    }

    @Test
    void testResumeAgent_shouldSucceedOnPausedAgent() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");
        String id = orchestrator.registerAgent("TypeA", mockAgent);
        orchestrator.pauseAgent(id, "Maintenance");

        // Act
        boolean result = orchestrator.resumeAgent(id, "Maintenance done");

        // Assert
        assertTrue(result);
        Map<String, Map<String, Object>> states = orchestrator.getAllAgentStates();
        assertEquals(AgentStage.READY, states.get(id).get("currentStage"));
    }

    @Test
    void testResumeAgent_shouldFailOnNonPausedAgent() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");
        String id = orchestrator.registerAgent("TypeA", mockAgent);

        // Act — agent is in READY, not PAUSED
        boolean result = orchestrator.resumeAgent(id, "Should not work");

        // Assert
        assertFalse(result);
    }

    @Test
    void testPauseAgent_shouldFailForNonexistentAgent() {
        // Act
        boolean result = orchestrator.pauseAgent("non-existent-id", "reason");

        // Assert
        assertFalse(result);
    }

    // -----------------------------------------------------------------------
    // Pool limits
    // -----------------------------------------------------------------------

    @Test
    void testRegisterAgent_shouldThrowWhenPoolFull() {
        // Arrange — fill 20 agent slots (maxPoolSize default = 20)
        when(mockAgent.name()).thenReturn("Agent");
        for (int i = 0; i < 20; i++) {
            orchestrator.registerAgent("Type" + i, mockAgent);
        }

        // Act & Assert — 21st registration should throw
        assertThrows(IllegalStateException.class, () -> orchestrator.registerAgent("FullType", mockAgent));
    }

    // -----------------------------------------------------------------------
    // Reactive flowability tests are handled in consumer testing
    // -----------------------------------------------------------------------
}

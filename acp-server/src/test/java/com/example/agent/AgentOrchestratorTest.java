package com.example.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.agent.enums.AgentStage;
import com.example.agent.service.LogToMemoryAgentWorker;
import com.example.agent.service.SessionService;
import com.example.agent.util.CentralExecutorRegistry;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;

/**
 * Unit tests for AgentOrchestrator.
 *
 * Because AgentOrchestrator's constructor creates a JedisPool and launches
 * background timers, we cannot unit-test it without either:
 * (a) a running Redis instance, or
 * (b) refactoring the constructor to accept JedisPool as a dependency.
 *
 * These tests therefore focus on the public-facing API behaviour that
 * can be exercised synchronously without a live Redis connection:
 * registerAgent, getStatistics, getAllAgentStates, pauseAgent, resumeAgent,
 * and terminateAgent.
 *
 * Note: The AgentWrapper constructor calls persistSnapshot(), which tolerates
 * null snapshotRepo/logRepo (guarded by null-checks). The JedisPool created
 * by the orchestrator constructor may fail to connect, but the test assertions
 * run before any background timer fires, so we should be fine.
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
    private LogToMemoryAgentWorker logToMemoryAgentWorker;

    private AgentOrchestrator orchestrator;
    private LlmAgent mockAgent;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(snapshotRepo, logRepo, sessionService,
                executorRegistry, logToMemoryAgentWorker);
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
        String id1 = orchestrator.registerAgent(mockAgent);
        String id2 = orchestrator.registerAgent(mockAgent);

        // Assert
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "Each registration should get a unique ID");
    }

    @Test
    void testRegisterAgent_shouldIncrementStatistics() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        orchestrator.registerAgent(mockAgent);

        // Assert
        Map<String, Object> stats = orchestrator.getStatistics();
        assertEquals(1, stats.get("totalAgents"));
        assertEquals(1, stats.get("availableAgents"));
        assertEquals(0, stats.get("busyAgents"));
        assertEquals(1, stats.get("totalAgentsCreated"));
    }

    @Test
    void testRegisterAgent_withSameAgent_shouldCreateSeparateRegistrations() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        String id1 = orchestrator.registerAgent(mockAgent);
        String id2 = orchestrator.registerAgent(mockAgent);

        // Assert — both unique IDs exist in the pool
        Map<String, Object> stats = orchestrator.getStatistics();
        assertEquals(2, stats.get("totalAgents"));
        assertNotEquals(id1, id2);
    }

    @Test
    void testRegisterAgent_shouldAppearInAllAgentStates() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");

        // Act
        String id = orchestrator.registerAgent(mockAgent);

        // Assert
        Map<String, Map<String, Object>> states = orchestrator.getAllAgentStates();
        assertTrue(states.containsKey(id));
        Map<String, Object> agentState = states.get(id);
        assertEquals(id, agentState.get("agentId"));
        assertEquals("Test Agent", agentState.get("agentName"));
        assertEquals(AgentStage.READY, agentState.get("currentStage"));
    }

    // -----------------------------------------------------------------------
    // pauseAgent / resumeAgent / terminateAgent tests
    // -----------------------------------------------------------------------

    @Test
    void testPauseAgent_shouldSucceedOnReadyAgent() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");
        String id = orchestrator.registerAgent(mockAgent);

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
        String id = orchestrator.registerAgent(mockAgent);
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
        String id = orchestrator.registerAgent(mockAgent);

        // Act — agent is in READY, not PAUSED
        boolean result = orchestrator.resumeAgent(id, "Should not work");

        // Assert
        assertFalse(result);
    }

    @Test
    void testTerminateAgent_shouldRemoveFromPool() {
        // Arrange
        when(mockAgent.name()).thenReturn("Test Agent");
        String id = orchestrator.registerAgent(mockAgent);

        // Act
        boolean result = orchestrator.terminateAgent(id, "Shutting down");

        // Assert
        assertTrue(result);
        Map<String, Object> stats = orchestrator.getStatistics();
        assertEquals(0, stats.get("totalAgents"));
        assertEquals(1, stats.get("totalAgentsTerminated"));
    }

    @Test
    void testTerminateAgent_shouldFailForNonexistentAgent() {
        // Act
        boolean result = orchestrator.terminateAgent("non-existent-id", "reason");

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
    // getStatistics tests
    // -----------------------------------------------------------------------

    @Test
    void testGetStatistics_shouldReturnInitialValues() {
        // Act
        Map<String, Object> stats = orchestrator.getStatistics();

        // Assert
        assertEquals(0, stats.get("totalAgents"));
        assertEquals(0, stats.get("availableAgents"));
        assertEquals(0, stats.get("busyAgents"));
        assertEquals(10, stats.get("maxPoolSize"));
        assertEquals(0, stats.get("totalTasksExecuted"));
        assertEquals(0, stats.get("totalAgentsCreated"));
        assertEquals(0, stats.get("totalAgentsTerminated"));
    }

    // -----------------------------------------------------------------------
    // Pool limits
    // -----------------------------------------------------------------------

    @Test
    void testRegisterAgent_shouldThrowWhenPoolFull() {
        // Arrange — fill 10 agent slots (maxPoolSize = 10)
        when(mockAgent.name()).thenReturn("Agent");
        for (int i = 0; i < 10; i++) {
            orchestrator.registerAgent(mockAgent);
        }

        // Act & Assert — 11th registration should throw
        assertThrows(IllegalStateException.class, () -> orchestrator.registerAgent(mockAgent));
    }
}

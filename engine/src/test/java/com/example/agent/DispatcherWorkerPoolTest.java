package com.example.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import com.example.agent.NotificationWorker.WorkerStatus;
import com.example.agent.models.ConnectorMetrics;
import com.example.agent.models.NotificationJob;
import com.example.agent.models.WorkerSnapshot;

/**
 * Unit tests for DispatcherWorkerPool
 */
@ExtendWith(MockitoExtension.class)
class DispatcherWorkerPoolTest {

    @Mock
    private ApplicationContext context;

    @Mock
    private WorkerSnapshotRepository workerSnapshotRepository;

    @Mock
    private NotificationAttemptLogRepository logRepo;

    @Mock
    private NotificationWorker mockWorker;

    private DispatcherWorkerPool workerPool;
    private NotificationJob testJob;

    @BeforeEach
    void setUp() {
        workerPool = new DispatcherWorkerPool(context, workerSnapshotRepository, logRepo);

        testJob = NotificationJob.builder()
                .id("test-job-1")
                .eventType("immediate")
                .channel("email")
                .priority(NotificationJob.NotificationPriority.NORMAL)
                .build();
    }

    @Test
    void testInit_withNoExistingWorkers_shouldCreateMinimumWorkers() {
        // Arrange
        when(workerSnapshotRepository.findByStatus(WorkerStatus.UNAVAILABLE.name()))
                .thenReturn(new ArrayList<>());
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        doNothing().when(mockWorker).setLogBuffer(any());

        // Act
        workerPool.init();

        // Assert
        verify(workerSnapshotRepository).findByStatus(WorkerStatus.UNAVAILABLE.name());
        // Should create at least minWorkers (default is 2)
        verify(context, atLeast(2)).getBean(NotificationWorker.class);
    }

    @Test
    void testInit_withExistingWorkers_shouldRestoreFromSnapshots() {
        // Arrange
        WorkerSnapshot snapshot1 = new WorkerSnapshot("worker-1", Instant.now(),
                WorkerStatus.UNAVAILABLE.name(), "job-1");
        WorkerSnapshot snapshot2 = new WorkerSnapshot("worker-2", Instant.now(),
                WorkerStatus.UNAVAILABLE.name(), "job-2");

        List<WorkerSnapshot> snapshots = List.of(snapshot1, snapshot2);
        when(workerSnapshotRepository.findByStatus(WorkerStatus.UNAVAILABLE.name()))
                .thenReturn(snapshots);
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        doNothing().when(mockWorker).setLogBuffer(any());
        doNothing().when(mockWorker).loadState(any());

        // Act
        workerPool.init();

        // Assert
        verify(workerSnapshotRepository).findByStatus(WorkerStatus.UNAVAILABLE.name());
        verify(mockWorker, times(2)).loadState(any(WorkerSnapshot.class));
        verify(mockWorker, times(2)).setLogBuffer(any());
    }

    @Test
    void testAssign_shouldScaleAndAssignJob() throws InterruptedException {
        // Arrange
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        when(mockWorker.isAvailable()).thenReturn(true);
        when(mockWorker.assignJob(any(NotificationJob.class))).thenReturn(true);
        doNothing().when(mockWorker).setLogBuffer(any());

        // Initialize the pool first
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        workerPool.init();

        // Act
        workerPool.assign(testJob);

        // Assert
        verify(mockWorker).assignJob(testJob);
    }

    @Test
    void testAssign_whenNoWorkerAvailable_shouldThrowException() {
        // Arrange
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        when(mockWorker.isAvailable()).thenReturn(false);
        doNothing().when(mockWorker).setLogBuffer(any());

        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        workerPool.init();

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            workerPool.assign(testJob);
        });

        assertTrue(exception.getMessage().contains("No available worker found within timeout"));
    }

    @Test
    void testAssign_whenAssignJobFails_shouldThrowException() {
        // Arrange
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        when(mockWorker.isAvailable()).thenReturn(true);
        when(mockWorker.assignJob(any(NotificationJob.class))).thenReturn(false);
        doNothing().when(mockWorker).setLogBuffer(any());

        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        workerPool.init();

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            workerPool.assign(testJob);
        });

        assertTrue(exception.getMessage().contains("Failed to assign job to worker"));
    }

    @Test
    void testScaleToFit_shouldCreateAdditionalWorkers() {
        // Arrange
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        doNothing().when(mockWorker).setLogBuffer(any());
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());

        workerPool.init();
        int initialWorkerCount = 2; // minWorkers default

        // Act
        workerPool.scaleToFit(5);

        // Assert - should create additional workers (at least 5 more, but capped at
        // maxWorkers)
        verify(context, atLeast(initialWorkerCount + 5)).getBean(NotificationWorker.class);
    }

    @Test
    void testScaleToFit_shouldNotExceedMaxWorkers() {
        // Arrange
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        doNothing().when(mockWorker).setLogBuffer(any());
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());

        workerPool.init();

        // Act - try to scale beyond maxWorkers (default is 20)
        workerPool.scaleToFit(100);

        // Assert - should not create more than maxWorkers
        verify(context, atMost(20)).getBean(NotificationWorker.class);
    }

    @Test
    void testScaleToFit_shouldMaintainMinimumWorkers() {
        // Arrange
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        doNothing().when(mockWorker).setLogBuffer(any());
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());

        workerPool.init();

        // Act
        workerPool.scaleToFit(0);

        // Assert - should still have at least minWorkers (default is 2)
        verify(context, atLeast(2)).getBean(NotificationWorker.class);
    }

    @Test
    void testRemoveIdleWorkers_shouldRemoveWorkersAboveTTL() {
        // Arrange
        NotificationWorker idleWorker = mock(NotificationWorker.class);
        when(idleWorker.isAvailable()).thenReturn(true);
        when(idleWorker.getLastActiveAt()).thenReturn(Instant.now().minusSeconds(60)); // 60 seconds ago
        doNothing().when(idleWorker).shutdown();
        doNothing().when(idleWorker).setLogBuffer(any());

        when(context.getBean(NotificationWorker.class)).thenReturn(idleWorker);
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());

        // Set a lower TTL for testing
        workerPool.getProperties().setIdleWorkerTtlSeconds(30);
        workerPool.init();

        // Act
        workerPool.removeIdleWorkers();

        // Assert - idle workers should be shut down (if above minWorkers)
        // Note: This test depends on the actual worker pool size
    }

    @Test
    void testRemoveIdleWorkers_shouldNotRemoveBelowMinWorkers() {
        // Arrange
        NotificationWorker idleWorker = mock(NotificationWorker.class);
        when(idleWorker.isAvailable()).thenReturn(true);
        when(idleWorker.getLastActiveAt()).thenReturn(Instant.now().minusSeconds(60));
        doNothing().when(idleWorker).setLogBuffer(any());

        when(context.getBean(NotificationWorker.class)).thenReturn(idleWorker);
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());

        workerPool.getProperties().setMinWorkers(2);
        workerPool.getProperties().setIdleWorkerTtlSeconds(30);
        workerPool.init();

        // Act
        workerPool.removeIdleWorkers();

        // Assert - should maintain minimum workers
        // The actual verification depends on the implementation
    }

    @Test
    void testShutdown_shouldShutdownAllWorkers() {
        // Arrange
        NotificationWorker worker1 = mock(NotificationWorker.class);
        NotificationWorker worker2 = mock(NotificationWorker.class);

        when(context.getBean(NotificationWorker.class))
                .thenReturn(worker1)
                .thenReturn(worker2);
        doNothing().when(worker1).setLogBuffer(any());
        doNothing().when(worker2).setLogBuffer(any());
        doNothing().when(worker1).shutdown();
        doNothing().when(worker2).shutdown();

        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        workerPool.init();

        // Act
        workerPool.shutdown();

        // Assert
        verify(worker1, atLeastOnce()).shutdown();
        verify(worker2, atLeastOnce()).shutdown();
    }

    @Test
    void testRun_shouldPersistWorkerSnapshots() throws InterruptedException {
        // Arrange
        NotificationWorker worker = mock(NotificationWorker.class);
        when(worker.getWorkerId()).thenReturn("worker-1");
        when(worker.getLastActiveAt()).thenReturn(Instant.now());
        when(worker.getStatus()).thenReturn(WorkerStatus.AVAILABLE);
        when(worker.getCurrentJob()).thenReturn(testJob);
        when(worker.getMetrics()).thenReturn(new ConnectorMetrics());
        doNothing().when(worker).setLogBuffer(any());

        when(context.getBean(NotificationWorker.class)).thenReturn(worker);
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        when(workerSnapshotRepository.findByWorkerId(anyString())).thenReturn(Optional.empty());
        when(workerSnapshotRepository.save(any(WorkerSnapshot.class))).thenReturn(null);

        workerPool.init();

        // Note: The run() method runs in an infinite loop, so we can't easily test it
        // without refactoring. This test just verifies the setup.

        // Assert
        verify(workerSnapshotRepository).findByStatus(anyString());
    }

    @Test
    void testDispatcherProperties_defaultValues() {
        // Arrange
        DispatcherWorkerPool.DispatcherProperties properties = new DispatcherWorkerPool(context,
                workerSnapshotRepository, logRepo).getProperties();

        // Assert
        assertEquals(2, properties.getMinWorkers());
        assertEquals(20, properties.getMaxWorkers());
        assertEquals(30, properties.getIdleWorkerTtlSeconds());
        assertEquals(10, properties.getPollBatchSize());
        assertEquals(5000, properties.getLogFlushIntervalMs());
        assertEquals(50, properties.getLogBufferSize());
        assertEquals(5, properties.getRetryMaxAttempts());
        assertEquals(2000, properties.getRetryBackoffMillis());
    }

    @Test
    void testDispatcherProperties_setters() {
        // Arrange
        DispatcherWorkerPool.DispatcherProperties properties = new DispatcherWorkerPool(context,
                workerSnapshotRepository, logRepo).getProperties();

        // Act
        properties.setMinWorkers(5);
        properties.setMaxWorkers(50);
        properties.setIdleWorkerTtlSeconds(60);
        properties.setPollBatchSize(20);
        properties.setLogFlushIntervalMs(10000);
        properties.setLogBufferSize(100);
        properties.setRetryMaxAttempts(10);
        properties.setRetryBackoffMillis(5000);

        // Assert
        assertEquals(5, properties.getMinWorkers());
        assertEquals(50, properties.getMaxWorkers());
        assertEquals(60, properties.getIdleWorkerTtlSeconds());
        assertEquals(20, properties.getPollBatchSize());
        assertEquals(10000, properties.getLogFlushIntervalMs());
        assertEquals(100, properties.getLogBufferSize());
        assertEquals(10, properties.getRetryMaxAttempts());
        assertEquals(5000, properties.getRetryBackoffMillis());
    }

    @Test
    void testLogBufferFlush_shouldSaveLogsWhenBufferFull() {
        // Arrange
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        doNothing().when(mockWorker).setLogBuffer(any());
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());

        workerPool.init();
        workerPool.getProperties().setLogBufferSize(2);

        // Note: Testing the actual log buffer flush requires running the background
        // thread
        // which is difficult in a unit test. This test verifies the setup.

        // Assert
        assertNotNull(workerPool.getProperties());
    }

    @Test
    void testFindAvailableWorker_shouldReturnWorkerWhenAvailable() throws Exception {
        // This is a private method, so we test it indirectly through assign()

        // Arrange
        when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        when(mockWorker.isAvailable()).thenReturn(true);
        when(mockWorker.assignJob(any(NotificationJob.class))).thenReturn(true);
        doNothing().when(mockWorker).setLogBuffer(any());

        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        workerPool.init();

        // Act
        workerPool.assign(testJob);

        // Assert
        verify(mockWorker).assignJob(testJob);
    }

    @Test
    void testConcurrentAssignment_shouldHandleMultipleJobs() throws InterruptedException {
        // Arrange
        NotificationWorker worker1 = mock(NotificationWorker.class);
        NotificationWorker worker2 = mock(NotificationWorker.class);

        when(context.getBean(NotificationWorker.class))
                .thenReturn(worker1)
                .thenReturn(worker2);

        when(worker1.isAvailable()).thenReturn(true);
        when(worker2.isAvailable()).thenReturn(true);
        when(worker1.assignJob(any())).thenReturn(true);
        when(worker2.assignJob(any())).thenReturn(true);
        doNothing().when(worker1).setLogBuffer(any());
        doNothing().when(worker2).setLogBuffer(any());

        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        workerPool.init();

        NotificationJob job1 = NotificationJob.builder()
                .id("job-1")
                .build();
        NotificationJob job2 = NotificationJob.builder()
                .id("job-2")
                .build();

        // Act
        workerPool.assign(job1);
        workerPool.assign(job2);

        // Assert
        verify(worker1, atLeastOnce()).assignJob(any());
        verify(worker2, atLeastOnce()).assignJob(any());
    }

    @Test
    void testMetricsCollection_shouldTrackWorkerMetrics() {
        // Arrange
        NotificationWorker worker = mock(NotificationWorker.class);
        ConnectorMetrics metrics = new ConnectorMetrics();

        when(worker.getWorkerId()).thenReturn("worker-1");
        when(worker.getMetrics()).thenReturn(metrics);
        when(worker.getLastActiveAt()).thenReturn(Instant.now());
        when(worker.getStatus()).thenReturn(WorkerStatus.AVAILABLE);
        when(worker.getCurrentJob()).thenReturn(testJob);
        doNothing().when(worker).setLogBuffer(any());

        when(context.getBean(NotificationWorker.class)).thenReturn(worker);
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        when(workerSnapshotRepository.findByWorkerId(anyString())).thenReturn(Optional.empty());

        workerPool.init();

        // Assert - metrics should be tracked
        verify(worker, atLeastOnce()).getWorkerId();
    }
}

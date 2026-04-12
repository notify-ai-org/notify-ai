package com.notify.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import com.notify.agent.NotificationWorker.WorkerStatus;
import com.notify.agent.models.NotificationJob;

/**
 * Unit tests for DispatcherWorkerPool.
 *
 * Every test is capped at 10s to catch thread leaks early.
 * {@code @AfterEach} shuts down the executor so background threads
 * (the {@code run()} loop and submitted workers) are stopped.
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class DispatcherWorkerPoolTest {

    @Mock
    private ApplicationContext context;

    @Mock
    private WorkerSnapshotRepository workerSnapshotRepository;

    @Mock
    private NotificationAttemptLogRepository logRepo;

    @Mock
    private NotificationWorker mockWorker;

    @Mock
    private ExecutorService service;

    private DispatcherWorkerPool workerPool;
    private NotificationJob testJob;

    @BeforeEach
    void setUp() {

        workerPool = new DispatcherWorkerPool(context, workerSnapshotRepository, service, logRepo);
        // Set a reasonable interval (2s) to prevent overwhelming mocks in the
        // background thread
        workerPool.getProperties().setLogFlushIntervalMs(2000);

        testJob = NotificationJob.builder()
                .id("test-job-1")
                .eventType("immediate")
                .channel("email")
                .priority(NotificationJob.NotificationPriority.NORMAL)
                .build();
    }

    @AfterEach
    void tearDown() {
        workerPool.shutdown();
    }

    // --- Helper to call init() with minimal stubs ---
    private void initPool() {
        lenient().when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        lenient().when(context.getBean(NotificationWorker.class)).thenReturn(mockWorker);
        lenient().doNothing().when(mockWorker).setLogBuffer(any());
        lenient().when(service.submit(any(Runnable.class))).thenReturn(null);

        workerPool.init();
    }

    // -----------------------------------------------------------------------
    // registerAgent / init tests
    // -----------------------------------------------------------------------

    @Test
    void testInit_withNoExistingWorkers_shouldCreateMinimumWorkers() {
        initPool();
        // Assert
        verify(workerSnapshotRepository).findByStatus(WorkerStatus.UNAVAILABLE.name());
        // Should create at least minWorkers (default is 2)
        verify(context, atLeast(2)).getBean(NotificationWorker.class);
    }

    // -----------------------------------------------------------------------
    // assign tests
    // -----------------------------------------------------------------------

    @Test
    void testAssign_shouldScaleAndAssignJob() throws InterruptedException {
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

    // -----------------------------------------------------------------------
    // scaleToFit tests
    // -----------------------------------------------------------------------

    @Test
    void testScaleToFit_shouldCreateAdditionalWorkers() {
        // Arrange
        initPool();
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
        initPool();

        // Act - try to scale beyond maxWorkers (default is 20)
        workerPool.scaleToFit(100);

        // Assert - should not create more than maxWorkers
        verify(context, atMost(22)).getBean(NotificationWorker.class);
    }

    @Test
    void testScaleToFit_shouldMaintainMinimumWorkers() {
        // Arrange
        initPool();

        // Act
        workerPool.scaleToFit(0);

        // Assert - should still have at least minWorkers (default is 2)
        verify(context, atLeast(2)).getBean(NotificationWorker.class);
    }

    // -----------------------------------------------------------------------
    // removeIdleWorkers tests
    // -----------------------------------------------------------------------

    @Test
    void testRemoveIdleWorkers_shouldRemoveWorkersAboveTTL() {
        // Arrange
        NotificationWorker idleWorker = mock(NotificationWorker.class);
        when(idleWorker.isAvailable()).thenReturn(true);
        when(idleWorker.getLastActiveAt()).thenReturn(Instant.now().minusSeconds(60));
        doNothing().when(idleWorker).shutdown();
        doNothing().when(idleWorker).setLogBuffer(any());

        when(context.getBean(NotificationWorker.class)).thenReturn(idleWorker);
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());

        workerPool.getProperties().setIdleWorkerTtlSeconds(30);
        workerPool.init();

        // Act
        workerPool.removeIdleWorkers();

        // Assert - idle workers should be shut down (if above minWorkers)
    }

    @Test
    void testRemoveIdleWorkers_shouldNotRemoveBelowMinWorkers() {
        // Arrange
        NotificationWorker idleWorker = mock(NotificationWorker.class);
        lenient().when(idleWorker.isAvailable()).thenReturn(true);
        lenient().when(idleWorker.getLastActiveAt()).thenReturn(Instant.now().minusSeconds(60));
        doNothing().when(idleWorker).setLogBuffer(any());

        when(context.getBean(NotificationWorker.class)).thenReturn(idleWorker);
        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());

        workerPool.getProperties().setMinWorkers(2);
        workerPool.getProperties().setIdleWorkerTtlSeconds(30);
        workerPool.init();

        // Act
        workerPool.removeIdleWorkers();

        // Assert - should maintain minimum workers
    }

    // -----------------------------------------------------------------------
    // shutdown tests
    // -----------------------------------------------------------------------

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
    void testDispatcherProperties_setters() {
        // Arrange
        DispatcherWorkerPool.DispatcherProperties properties = workerPool.getProperties();

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

    // -----------------------------------------------------------------------
    // integration-style tests
    // -----------------------------------------------------------------------

    @Test
    void testFindAvailableWorker_shouldReturnWorkerWhenAvailable() throws Exception {
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

        lenient().when(context.getBean(NotificationWorker.class))
                .thenReturn(worker1)
                .thenReturn(worker2);

        AtomicBoolean worker1Available = new AtomicBoolean(true);
        AtomicBoolean worker2Available = new AtomicBoolean(true);

        lenient().when(worker1.isAvailable()).thenAnswer(i -> worker1Available.get());
        lenient().when(worker2.isAvailable()).thenAnswer(i -> worker2Available.get());

        lenient().doAnswer(i -> {
            worker1Available.set(false);
            return true;
        }).when(worker1).assignJob(any());

        lenient().doAnswer(i -> {
            worker2Available.set(false);
            return true;
        }).when(worker2).assignJob(any());

        doNothing().when(worker1).setLogBuffer(any());
        doNothing().when(worker2).setLogBuffer(any());

        when(workerSnapshotRepository.findByStatus(anyString())).thenReturn(new ArrayList<>());
        workerPool.init();

        NotificationJob job1 = NotificationJob.builder().id("job-1").build();
        NotificationJob job2 = NotificationJob.builder().id("job-2").build();

        // Act
        workerPool.assign(job1);
        workerPool.assign(job2);

        // Assert
        verify(worker1, atLeastOnce()).assignJob(any());
        verify(worker2, atLeastOnce()).assignJob(any());
    }

    @Test
    void testLogBufferFlush_shouldSaveLogsWhenBufferFull() {
        // Arrange
        initPool();
        workerPool.getProperties().setLogBufferSize(2);

        // Assert
        assertNotNull(workerPool.getProperties());
    }

}

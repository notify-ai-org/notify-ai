package com.example.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerListener;

import com.example.agent.interfaces.DeadLetterManager;
import com.example.agent.models.EventSchedule;
import com.example.agent.models.NotificationJob;

/**
 * Unit tests for NotificationDispatcher
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private DispatcherWorkerPool workerPool;

    @Mock
    private Scheduler quartzScheduler;

    @Mock
    private EventScheduleRepository eventScheduleRepository;

    @Mock
    private DeadLetterManager deadLetterManager;

    @Mock
    private NotificationJobRepository notificationJobRepo;

    @InjectMocks
    private NotificationDispatcher dispatcher;

    private NotificationJob testJob;
    private EventSchedule testSchedule;

    @BeforeEach
    void setUp() {
        testJob = NotificationJob.builder()
                .id("test-job-1")
                .eventType("immediate")
                .channel("email")
                .priority(NotificationJob.NotificationPriority.NORMAL)
                .build();

        testSchedule = new EventSchedule();
        testSchedule.setId("schedule-1");
        testSchedule.setEventName("test-event");
        testSchedule.setCronExpression("0 0 12 * * ?");
    }

    @Test
    void testStart_shouldStartSchedulerAndLoadSchedules() throws SchedulerException {
        // Arrange
        List<EventSchedule> schedules = Arrays.asList(testSchedule);
        when(eventScheduleRepository.findAll()).thenReturn(schedules);
        when(quartzScheduler.getListenerManager()).thenReturn(mock(org.quartz.ListenerManager.class));

        // Act
        dispatcher.start();

        // Assert
        verify(quartzScheduler).start();
        verify(quartzScheduler.getListenerManager()).addTriggerListener(any(TriggerListener.class));
        verify(eventScheduleRepository).findAll();
        verify(quartzScheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void testStart_shouldHandleSchedulerException() throws SchedulerException {
        // Arrange
        doThrow(new SchedulerException("Test exception")).when(quartzScheduler).start();

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> dispatcher.start());
    }

    @Test
    void testPushJob_withDefaultPriority_shouldAssignToWorkerPool() {
        // Arrange
        doNothing().when(workerPool).assign(any(NotificationJob.class));

        // Act
        dispatcher.pushJob(testJob);

        // Assert
        verify(workerPool).assign(testJob);
    }

    @Test
    void testPushJob_withPriority_shouldAssignToWorkerPool() {
        // Arrange
        double priority = 5.0;
        doNothing().when(workerPool).assign(any(NotificationJob.class));

        // Act
        dispatcher.pushJob(testJob, priority);

        // Assert
        verify(workerPool).assign(testJob);
    }

    @Test
    void testPushJob_whenWorkerPoolThrowsException_shouldThrowRuntimeException() {
        // Arrange
        doThrow(new RuntimeException("Worker pool error")).when(workerPool).assign(any(NotificationJob.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dispatcher.pushJob(testJob);
        });

        assertTrue(exception.getMessage().contains("Failed to push job to queue"));
    }

    @Test
    void testPushJob_withDelay_shouldWaitAndAssignToWorkerPool() throws InterruptedException {
        // Arrange
        long delayMillis = 100;
        doNothing().when(workerPool).assign(any(NotificationJob.class));

        // Act
        long startTime = System.currentTimeMillis();
        dispatcher.pushJob(testJob, 1.0, delayMillis);
        long endTime = System.currentTimeMillis();

        // Assert
        verify(workerPool).assign(testJob);
        assertTrue(endTime - startTime >= delayMillis, "Delay should be at least " + delayMillis + "ms");
    }

    @Test
    void testScheduleJob_withCronExpression_shouldScheduleWithCron() throws SchedulerException {
        // Arrange
        ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

        // Act
        dispatcher.scheduleJob(testSchedule);

        // Assert
        verify(quartzScheduler).scheduleJob(jobCaptor.capture(), triggerCaptor.capture());

        JobDetail capturedJob = jobCaptor.getValue();
        assertEquals("job_" + testSchedule.getId(), capturedJob.getKey().getName());
        assertEquals(testSchedule.getId(), capturedJob.getJobDataMap().getString("scheduleId"));
    }

    @Test
    void testScheduleJob_withScheduledAt_shouldScheduleAtSpecificTime() throws SchedulerException {
        // Arrange
        testSchedule.setCronExpression(null);
        testSchedule.setScheduledAt(Instant.now().plusSeconds(3600));
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

        // Act
        dispatcher.scheduleJob(testSchedule);

        // Assert
        verify(quartzScheduler).scheduleJob(any(JobDetail.class), triggerCaptor.capture());
        assertNotNull(triggerCaptor.getValue().getStartTime());
    }

    @Test
    void testScheduleJob_withNoScheduleInfo_shouldScheduleImmediately() throws SchedulerException {
        // Arrange
        testSchedule.setCronExpression(null);
        testSchedule.setScheduledAt(null);

        // Act
        dispatcher.scheduleJob(testSchedule);

        // Assert
        verify(quartzScheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void testScheduleJob_whenSchedulerThrowsException_shouldHandleGracefully() throws SchedulerException {
        // Arrange
        when(quartzScheduler.scheduleJob(any(JobDetail.class), any(Trigger.class)))
                .thenThrow(new SchedulerException("Test exception"));

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> dispatcher.scheduleJob(testSchedule));
    }

    @Test
    void testShutdown_shouldShutdownSchedulerAndWorkerPool() throws SchedulerException {
        // Act
        dispatcher.shutdown();

        // Assert
        verify(quartzScheduler).shutdown();
        verify(workerPool).shutdown();
    }

    @Test
    void testShutdown_whenSchedulerThrowsException_shouldStillShutdownWorkerPool() throws SchedulerException {
        // Arrange
        doThrow(new SchedulerException("Test exception")).when(quartzScheduler).shutdown();

        // Act
        dispatcher.shutdown();

        // Assert
        verify(quartzScheduler).shutdown();
        verify(workerPool).shutdown();
    }

    // @Test
    // void testPurgeExpiredNotificationJobs_shouldDeleteExpiredJobs() throws
    // Exception {
    // // Arrange
    // NotificationJob expiredJob = NotificationJob.builder()
    // .id("expired-job")
    // .build();

    // NotificationJob validJob = NotificationJob.builder()
    // .id("valid-job")
    // .build();

    // List<NotificationJob> allJobs = Arrays.asList(expiredJob, validJob);
    // when(notificationJobRepo.findAll()).thenReturn(allJobs);

    // // Create a test dispatcher that can access the protected method
    // NotificationDispatcher testDispatcher = new NotificationDispatcher(
    // workerPool, quartzScheduler, eventScheduleRepository,
    // deadLetterManager, notificationJobRepo);

    // // Act
    // testDispatcher.purgeExpiredNotificationJobs();

    // // Assert
    // verify(notificationJobRepo).findAll();
    // // Note: The actual deletion depends on whether NotificationJob has
    // // getExpiresAt() method
    // }

    @Test
    void testSetCleanerIntervalMs_shouldUpdateInterval() {
        // Arrange
        long newInterval = 5000L;

        // Act
        dispatcher.setCleanerIntervalMs(newInterval);

        // Assert - no exception should be thrown
        assertDoesNotThrow(() -> dispatcher.setCleanerIntervalMs(newInterval));
    }

    @Test
    void testNoOpJob_shouldExecuteWithoutError() {
        // Arrange
        NotificationDispatcher.NoOpJob noOpJob = new NotificationDispatcher.NoOpJob();
        org.quartz.JobExecutionContext context = mock(org.quartz.JobExecutionContext.class);

        // Act & Assert
        assertDoesNotThrow(() -> noOpJob.execute(context));
    }

    /**
     * Test for QueueingTriggerListener
     */
    @Test
    void testQueueingTriggerListener_getName_shouldReturnCorrectName() {
        // Arrange
        NotificationDispatcher.QueueingTriggerListener listener = new NotificationDispatcher.QueueingTriggerListener(
                eventScheduleRepository, dispatcher, notificationJobRepo, deadLetterManager);

        // Act
        String name = listener.getName();

        // Assert
        assertEquals("QueueingTriggerListener", name);
    }

    @Test
    void testQueueingTriggerListener_triggerFired_withDeferredEvent_shouldPushJob() {
        // Arrange
        NotificationDispatcher.QueueingTriggerListener listener = new NotificationDispatcher.QueueingTriggerListener(
                eventScheduleRepository, dispatcher, notificationJobRepo, deadLetterManager);

        Trigger trigger = mock(Trigger.class);
        org.quartz.JobExecutionContext context = mock(org.quartz.JobExecutionContext.class);
        org.quartz.JobDetail jobDetail = mock(org.quartz.JobDetail.class);
        org.quartz.JobDataMap jobDataMap = new org.quartz.JobDataMap();
        jobDataMap.put("scheduleId", "schedule-1");

        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        when(eventScheduleRepository.findById("schedule-1")).thenReturn(Optional.of(testSchedule));

        NotificationJob deferredJob = NotificationJob.builder()
                .eventType("deffered")
                .eventName("test-event")
                .build();
        when(notificationJobRepo.findByEventName("test-event")).thenReturn(Optional.of(deferredJob));

        // Act
        listener.triggerFired(trigger, context);

        // Assert
        verify(eventScheduleRepository).findById("schedule-1");
        verify(notificationJobRepo).findByEventName("test-event");
    }

    @Test
    void testQueueingTriggerListener_vetoJobExecution_shouldReturnFalse() {
        // Arrange
        NotificationDispatcher.QueueingTriggerListener listener = new NotificationDispatcher.QueueingTriggerListener(
                eventScheduleRepository, dispatcher, notificationJobRepo, deadLetterManager);

        Trigger trigger = mock(Trigger.class);
        org.quartz.JobExecutionContext context = mock(org.quartz.JobExecutionContext.class);

        // Act
        boolean result = listener.vetoJobExecution(trigger, context);

        // Assert
        assertFalse(result);
    }

    @Test
    void testQueueingTriggerListener_triggerComplete_shouldNotThrowException() {
        // Arrange
        NotificationDispatcher.QueueingTriggerListener listener = new NotificationDispatcher.QueueingTriggerListener(
                eventScheduleRepository, dispatcher, notificationJobRepo, deadLetterManager);

        Trigger trigger = mock(Trigger.class);
        org.quartz.JobExecutionContext context = mock(org.quartz.JobExecutionContext.class);
        Trigger.CompletedExecutionInstruction instruction = Trigger.CompletedExecutionInstruction.NOOP;

        // Act & Assert
        assertDoesNotThrow(() -> listener.triggerComplete(trigger, context, instruction));
    }
}

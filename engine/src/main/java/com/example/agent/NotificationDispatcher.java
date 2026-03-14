package com.example.agent;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.quartz.CalendarIntervalScheduleBuilder;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.example.agent.models.NotificationJob;
import com.example.agent.models.Event;
import com.example.agent.interfaces.DeadLetterManager;
import com.example.agent.models.EventSchedule;
import com.example.agent.exceptions.ValidationRequiredException;

@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final DispatcherWorkerPool workerPool;
    private final Scheduler quartzScheduler;
    private final EventScheduleRepository eventScheduleRepository;
    private final EventRepository eventRepository;
    private final DeadLetterManager deadLetterManager;
    private final NotificationJobRepository notificationJobRepo;
    // --- Cleaner thread for purging expired notification jobs ---
    private Thread cleanerThread;
    private volatile boolean cleanerRunning = true;
    // Defaults to 10min, but can be set via setter or config property
    private long cleanerIntervalMs = 10 * 60 * 1000L;

    /**
     * Set the interval (in ms) between cleaner runs.
     * Call before @PostConstruct/start().
     */
    public void setCleanerIntervalMs(long intervalMs) {
        this.cleanerIntervalMs = intervalMs;
    }

    @PostConstruct
    private void startCleanerThread() {
        cleanerRunning = true;
        cleanerThread = new Thread(() -> {
            while (cleanerRunning) {
                try {
                    purgeExpiredNotificationJobs();
                } catch (Exception e) {
                    // Log but don't kill thread
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(cleanerIntervalMs);
                } catch (InterruptedException ie) {
                    // Allow shutdown of thread
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ExpiredNotificationJobCleaner");
        cleanerThread.setDaemon(true);
        cleanerThread.start();
    }

    @PreDestroy
    private void stopCleanerThread() {
        cleanerRunning = false;
        if (cleanerThread != null) {
            cleanerThread.interrupt();
        }
    }

    /**
     * Deletes expired notification jobs from the repository.
     * Override this logic if soft-delete is wanted.
     */
    protected void purgeExpiredNotificationJobs() {
        // This example assumes NotificationJob has a getExpiresAt() and
        // notificationJobRepo supports delete
        // and find operations. Adjust as needed for your repo.
        List<NotificationJob> all = notificationJobRepo.findAll();
        Instant now = Instant.now();
        for (NotificationJob job : all) {
            Instant expiresAt = null;
            try {
                expiresAt = (Instant) NotificationJob.class.getMethod("getExpiresAt").invoke(job);
            } catch (Exception ioe) {
                // If method unavailable, skip
                continue;
            }
            if (expiresAt != null && expiresAt.isBefore(now)) {
                // Try/catch to ensure one failure does not abort the others
                try {
                    notificationJobRepo.delete(job);
                } catch (Exception e) {
                    // Optionally log or track failed deletions
                    throw e;
                }
            }
        }
    }

    @PostConstruct
    public void start() {
        try {
            // Start Quartz Scheduler
            quartzScheduler.start();

            // Register TriggerListener
            quartzScheduler.getListenerManager().addTriggerListener(
                    new QueueingTriggerListener(eventScheduleRepository,
                            this,
                            notificationJobRepo,
                            deadLetterManager));

            // Load and schedule all persisted EventSchedules
            List<EventSchedule> schedules = eventScheduleRepository.findAll();
            for (EventSchedule schedule : schedules) {
                scheduleJob(schedule);
            }

        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Push a notification job to the queue.
     * 
     * @param job The notification job to push
     * @return The job ID returned by the queue service
     * @throws RuntimeException if serialization fails
     */
    public void pushJob(NotificationJob job) {
        pushJob(job, 0);
    }

    /**
     * Push a notification job to the queue with specified queue name and priority.
     * Validates that the associated event has been approved before dispatching.
     * 
     * @param job       The notification job to push
     * @param queueName The name of the queue (defaults to "notifications")
     * @param priority  The priority of the job (higher values = higher priority)
     * @return The job ID returned by the queue service
     * @throws RuntimeException            if serialization fails
     * @throws ValidationRequiredException if associated event is not validated
     */
    public void pushJob(NotificationJob job, double priority) {
        // Validation check: Ensure the associated event is validated
        if (job.getEventName() != null) {
            Event event = eventRepository.findByName(job.getEventName()).orElse(null);
            if (event != null && !event.isValidated()) {
                logger.warn("Skipping dispatch for job {} - Event '{}' (ID: {}) is not validated",
                        job.getId(), event.getName(), event.getId());
                throw new ValidationRequiredException("Event", event.getId());
            }
        }

        try {
            workerPool.assign(job);
            logger.info("Dispatched job {} for validated event '{}'", job.getId(), job.getEventName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to push job to queue", e);
        }
    }

    /**
     * Push a notification job to the queue with a delay.
     * 
     * @param job         The notification job to push
     * @param queueName   The name of the queue (defaults to "notifications")
     * @param priority    The priority of the job (higher values = higher priority)
     * @param delayMillis Delay in milliseconds before the job becomes available
     * @return The job ID returned by the queue service
     * @throws RuntimeException if serialization fails
     */
    public void pushJob(NotificationJob job, double priority, long delayMillis) {
        try {
            Thread.sleep(delayMillis);
            workerPool.assign(job);
        } catch (Exception e) {
            throw new RuntimeException("Failed to push job to queue", e);
        }
    }

    /**
     * Schedule a job using Quartz scheduler.
     * Only schedules the job if the EventSchedule has been validated.
     * 
     * @param schedule The event schedule to register
     * @throws SchedulerException
     */
    public void scheduleJob(EventSchedule schedule) throws SchedulerException {
        // Validation check: Only schedule validated schedules
        if (!schedule.isValidated()) {
            logger.warn("Skipping scheduling for EventSchedule '{}' (ID: {}) - not validated. "
                    + "Schedule will not execute until validated.",
                    schedule.getEventName(), schedule.getId());
            return;
        }

        try {
            JobDetail job = JobBuilder.newJob(NoOpJob.class)
                    .withIdentity("job_" + schedule.getId())
                    .usingJobData("scheduleId", schedule.getId())
                    .build();

            TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                    .withIdentity("trigger_" + schedule.getId())
                    .forJob(job);

            if (schedule.getCronExpression() != null && !schedule.getCronExpression().isEmpty()) {
                if (schedule.getTriggerType().equals("CRON")) {
                    triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(schedule.getCronExpression()));
                } else if (schedule.getTriggerType().equals("SIMPLE")) {
                    triggerBuilder.withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(Long.parseLong(schedule.getCronExpression()))
                            .repeatForever());
                }
            }

            if (schedule.getScheduledAt() != null) {
                triggerBuilder.startAt(java.util.Date.from(schedule.getScheduledAt()));
            } else {
                // Default to immediate if no schedule info (or handle as error)
                triggerBuilder.startNow();
            }

            quartzScheduler.scheduleJob(job, triggerBuilder.build());
            logger.info("Scheduled validated EventSchedule '{}' (ID: {})",
                    schedule.getEventName(), schedule.getId());
        } catch (SchedulerException e) {
            logger.error("Failed to schedule EventSchedule '{}': {}", schedule.getId(), e.getMessage());
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() throws SchedulerException {
        try {
            quartzScheduler.shutdown();
        } catch (SchedulerException e) {
            throw e;
        }
        workerPool.shutdown();
    }

    public static class NoOpJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
            // No-op, logic is in TriggerListener
        }
    }

    @RequiredArgsConstructor
    public static class QueueingTriggerListener implements TriggerListener {

        private final EventScheduleRepository eventScheduleRepository;
        private final NotificationDispatcher notificationDispatcher;
        private final NotificationJobRepository notificationJobRepo;
        private final DeadLetterManager deadLetterManager;

        @Override
        public String getName() {
            return "QueueingTriggerListener";
        }

        @Override
        public void triggerFired(Trigger trigger, JobExecutionContext context) {
            String scheduleId = context.getJobDetail().getJobDataMap().getString("scheduleId");
            // In a real scenario, we might fetch more details or construct a full
            // NotificationJob.
            EventSchedule schedule = eventScheduleRepository.findById(scheduleId).orElseThrow();

            // Recheck validation before execution (in case it was revoked after scheduling)
            if (!schedule.isValidated()) {
                logger.warn("EventSchedule '{}' (ID: {}) validation was revoked - skipping execution",
                        schedule.getEventName(), schedule.getId());
                return;
            }

            String eventName = schedule.getEventName();
            NotificationJob job = notificationJobRepo.findByEventName(eventName).orElseThrow();

            String eventTypeObj = job.getEventType();
            if (eventTypeObj != null && eventTypeObj.equals("deffered")) {
                try {
                    notificationDispatcher.pushJob(job);
                } catch (ValidationRequiredException e) {
                    logger.error("Cannot dispatch job for schedule '{}': {}",
                            schedule.getId(), e.getMessage());
                    throw e;
                }
            }

        }

        @Override
        public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
            return false;
        }

        @Override
        public void triggerMisfired(Trigger trigger) {
            String scheduleId = trigger.getJobKey().getName().split("-")[1];
            // In a real scenario, we might fetch more details or construct a full
            // NotificationJob.
            EventSchedule schedule = eventScheduleRepository.findById(scheduleId).orElseThrow();
            String eventName = schedule.getEventName();

            Instant firstAttemptAt = Instant.now();
            NotificationJob job = notificationJobRepo.findByEventName(eventName).orElseThrow();
            deadLetterManager.enqueue(
                    job,
                    new Exception(trigger.getDescription()),
                    0,
                    firstAttemptAt,
                    Instant.now(),
                    "misfire-" + trigger.getKey().getName(),
                    "dispatcher-1",
                    null,
                    job.getTemplate());
        }

        @Override
        public void triggerComplete(Trigger trigger, JobExecutionContext context,
                Trigger.CompletedExecutionInstruction triggerInstructionCode) {
        }
    }
}

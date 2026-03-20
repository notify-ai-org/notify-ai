package com.example.agent.util;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
public class CentralExecutorRegistry implements DisposableBean {

        private final Map<ExecutorType, ExecutorService> executors = new EnumMap<>(ExecutorType.class);

        public enum ExecutorType {
                CPU, // JSON parsing, rule evaluation
                IO, // DB, Redis, HTTP calls
                LLM, // log→fact, summarization
                SCHEDULER, // cron / delayed tasks
                DISPATCHER, // notification workers
                KAFKA, // heavy Kafka consumers
                RETRY // backoff / DLQ replays
        }

        record Config(int core, int max, int queue) {
        }

        record SchedulerConfig(int threads) {
        }

        @ConfigurationProperties(prefix = "executors")
        public record ExecutorProperties(
                        Config cpu,
                        Config io,
                        Config llm,
                        Config dispatcher,
                        SchedulerConfig scheduler) {
        }

        public CentralExecutorRegistry(ExecutorProperties props) {

                executors.put(ExecutorType.CPU,
                                new ThreadPoolExecutor(
                                                props.cpu().core(),
                                                props.cpu().max(),
                                                30, TimeUnit.SECONDS,
                                                new ArrayBlockingQueue<>(props.cpu().queue()),
                                                namedFactory("cpu"),
                                                new ThreadPoolExecutor.CallerRunsPolicy()));

                executors.put(ExecutorType.IO,
                                new ThreadPoolExecutor(
                                                props.io().core(),
                                                props.io().max(),
                                                60, TimeUnit.SECONDS,
                                                new ArrayBlockingQueue<>(props.io().queue()),
                                                namedFactory("io"),
                                                new ThreadPoolExecutor.AbortPolicy()));

                executors.put(ExecutorType.LLM,
                                new ThreadPoolExecutor(
                                                props.llm().core(),
                                                props.llm().max(),
                                                60, TimeUnit.SECONDS,
                                                new ArrayBlockingQueue<>(props.llm().queue()),
                                                namedFactory("llm-agent"),
                                                new ThreadPoolExecutor.CallerRunsPolicy()));

                executors.put(ExecutorType.DISPATCHER,
                                new ThreadPoolExecutor(
                                                props.dispatcher().core(),
                                                props.dispatcher().max(),
                                                60, TimeUnit.SECONDS,
                                                new ArrayBlockingQueue<>(props.dispatcher().queue()),
                                                namedFactory("dispatcher"),
                                                new ThreadPoolExecutor.CallerRunsPolicy()));

                executors.put(ExecutorType.SCHEDULER,
                                Executors.newScheduledThreadPool(
                                                props.scheduler().threads(),
                                                namedFactory("scheduler")));
        }

        public ExecutorService get(ExecutorType type) {
                return executors.get(type);
        }

        @Override
        public void destroy() {
                executors.values().forEach(executor -> {
                        executor.shutdown();
                });
        }

        private ThreadFactory namedFactory(String prefix) {
                return r -> {
                        Thread t = new Thread(r);
                        t.setName(prefix + "-" + t.getId());
                        t.setDaemon(false);
                        return t;
                };
        }
}

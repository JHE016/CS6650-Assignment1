package client;

import static config.SkierClientConfig.INITIAL_THREADS;

import config.SkierClientConfig;
import consumer.LogConsumer;
import consumer.SkierConsumerPart2;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import model.RequestLog;
import producer.SkierProducer;

public class SkierClientPart2 {
  private static final AtomicInteger successfulRequests = new AtomicInteger(0);
  private static final AtomicInteger failedRequests = new AtomicInteger(0);

  private static final HttpClient sharedClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .executor(Executors.newFixedThreadPool(100))
      .version(HttpClient.Version.HTTP_1_1)
      .build();

  public static void main(String[] args) {

    long startTime = System.currentTimeMillis();
    BlockingQueue<String> eventQueue = new LinkedBlockingQueue<>(SkierClientConfig.QUEUE_SIZE);
    ConcurrentLinkedQueue<RequestLog> logQueue = new ConcurrentLinkedQueue<>();

    // Start event producer thread
    Thread eventProducer = new Thread(new SkierProducer(eventQueue, SkierClientConfig.TOTAL_REQUESTS));
    eventProducer.start();

    // Phase 1: Initial 32 threads
    CountDownLatch initialPhaseLatch = new CountDownLatch(1);
    ExecutorService initialExecutor = Executors.newFixedThreadPool(INITIAL_THREADS);

    for (int i = 0; i < INITIAL_THREADS; i++) {
      initialExecutor.execute(new SkierConsumerPart2(eventQueue, successfulRequests, failedRequests, sharedClient, SkierClientConfig.INITIAL_REQUESTS_PER_THREAD, initialPhaseLatch, logQueue));
    }

    // Wait for one thread of phase 1 to complete then start phase2
    try {
      initialPhaseLatch.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Phase 2: Process remaining requests
    int remainingRequests = SkierClientConfig.TOTAL_REQUESTS - (INITIAL_THREADS * SkierClientConfig.INITIAL_REQUESTS_PER_THREAD);
    int requestsPerThread = remainingRequests / SkierClientConfig.SECOND_PHASE_THREADS;

    ExecutorService remainingExecutor = Executors.newFixedThreadPool(SkierClientConfig.SECOND_PHASE_THREADS);
    CountDownLatch remainingPhaseLatch = new CountDownLatch(SkierClientConfig.SECOND_PHASE_THREADS);

    for (int i = 0; i < SkierClientConfig.SECOND_PHASE_THREADS; i++) {
      remainingExecutor.execute(new SkierConsumerPart2(eventQueue, successfulRequests, failedRequests, sharedClient, requestsPerThread, remainingPhaseLatch, logQueue));
    }

    // Wait for all requests to complete
    try {
      remainingPhaseLatch.await();
      eventProducer.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Shutdown executors
    shutdownExecutor(initialExecutor, "Initial Phase");
    shutdownExecutor(remainingExecutor, "Remaining Phase");

    List<RequestLog> metricsLogs = new ArrayList<>(logQueue);

    Thread logWriterThread = new Thread(new LogConsumer(logQueue));
    logWriterThread.start();
    try {
      logWriterThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Calculate and print statistics
    long endTime = System.currentTimeMillis();
    long totalTime = endTime - startTime;
    double throughput = SkierClientConfig.TOTAL_REQUESTS / (totalTime / 1000.0);

    System.out.println("\nClient Part 2 Performance Results:");
    System.out.println("=============Total===============");
    System.out.println("Total successful requests: " + successfulRequests.get());
    System.out.println("Total failed requests: " + failedRequests.get());
    System.out.println("Total runtime: " + totalTime + " ms");
    System.out.printf("Throughput: %.2f requests/second%n", throughput);
    processLatencyMetrics(metricsLogs);
  }

  private static void processLatencyMetrics(List<RequestLog> logQueue) {
    List<Long> latencies = new ArrayList<>();
    for (RequestLog log : logQueue) {
      latencies.add(log.getLatency());
    }
    if (latencies.isEmpty()) {
      System.out.println("No latency data collected.");
      return;
    }

    Collections.sort(latencies);
    long minLatency = latencies.get(0);
    long maxLatency = latencies.get(latencies.size() - 1);
    double meanLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
    long medianLatency = latencies.get(latencies.size() / 2);
    long p99Latency = latencies.get((int) (latencies.size() * 0.99));

    System.out.println("\nResponse Metrics:");
    System.out.println("Min response time: " + minLatency + " ms");
    System.out.println("Max response time: " + maxLatency + " ms");
    System.out.printf("Mean response time: %.2f ms%n", meanLatency);
    System.out.println("Median response time: " + medianLatency + " ms");
    System.out.println("99th percentile response time: " + p99Latency + " ms");
  }

  private static void shutdownExecutor(ExecutorService executor, String phaseName) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        System.err.println(phaseName + " executor did not terminate in time. Forcing shutdown.");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      System.err.println(phaseName + " executor shutdown interrupted.");
      executor.shutdownNow();
    }
  }

}
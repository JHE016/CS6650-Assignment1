package client;

import static config.SkierClientConfig.INITIAL_THREADS;

import config.SkierClientConfig;
import consumer.SkierConsumer;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import producer.SkierProducer;

public class SkierClient {
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

    // Start event producer thread
    Thread eventProducer = new Thread(new SkierProducer(eventQueue, SkierClientConfig.TOTAL_REQUESTS));
    eventProducer.start();

    // Phase 1: Initial 32 threads
    CountDownLatch initialPhaseLatch = new CountDownLatch(1);
    ExecutorService initialExecutor = Executors.newFixedThreadPool(INITIAL_THREADS);

    for (int i = 0; i < INITIAL_THREADS; i++) {
      initialExecutor.execute(new SkierConsumer(eventQueue, successfulRequests, failedRequests, sharedClient, SkierClientConfig.INITIAL_REQUESTS_PER_THREAD, initialPhaseLatch));
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
      remainingExecutor.execute(new SkierConsumer(eventQueue, successfulRequests, failedRequests, sharedClient, requestsPerThread, remainingPhaseLatch));
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

    // Calculate and print statistics
    long endTime = System.currentTimeMillis();
    long totalTime = endTime - startTime;
    double throughput = SkierClientConfig.TOTAL_REQUESTS / (totalTime / 1000.0);

    System.out.println("\nClient Part 1 Performance Results:");
    System.out.println("=============Total===============");
    System.out.println("Total successful requests: " + successfulRequests.get());
    System.out.println("Total failed requests: " + failedRequests.get());
    System.out.println("Total runtime: " + totalTime + " ms");
    System.out.printf("Throughput: %.2f requests/second%n", throughput);
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
package consumer;

import static config.SkierClientConfig.MAX_RETRIES;

import api.RequestSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class SkierConsumer implements Runnable {
  private final BlockingQueue<String> eventQueue;
  private final AtomicInteger successfulRequests;
  private final AtomicInteger failedRequests;
  private final HttpClient sharedClient;

  private final CountDownLatch startLatch;
  private final int requestCount;

  public SkierConsumer(BlockingQueue<String> eventQueue, AtomicInteger successfulRequests, AtomicInteger failedRequests,
       HttpClient sharedClient, int requestCount, CountDownLatch startLatch) {
    this.eventQueue = eventQueue;
    this.successfulRequests = successfulRequests;
    this.failedRequests = failedRequests;
    this.sharedClient = sharedClient;
    this.requestCount = requestCount;
    this.startLatch = startLatch;
  }

  @Override
  public void run() {
    int processedRequests = 0;

    try {
      while (processedRequests < requestCount) {
        String event = eventQueue.take();

        String[] parts = event.split("::");
        String endpoint = parts[0];
        String jsonBody = parts[1];

        int responseCode = RequestSender.sendPostRequestWithRetry(sharedClient, endpoint, jsonBody);
        if (responseCode == 201) {
          successfulRequests.incrementAndGet();
        } else {
          failedRequests.incrementAndGet();
        }

        processedRequests++;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Request processing interrupted: " + e.getMessage());
    } finally {
      startLatch.countDown();
    }
  }
}
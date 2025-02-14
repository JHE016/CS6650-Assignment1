package client;

import static config.SkierClientConfig.INITIAL_THREADS;
import static config.SkierClientConfig.SECOND_PHASE_THREADS;

import api.RequestSender;
import config.SkierClientConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class SingleThreadClient {
  private static final int TOTAL_REQUESTS = 10000;
  private static final AtomicInteger successfulRequests = new AtomicInteger(0);
  private static final AtomicInteger failedRequests = new AtomicInteger(0);


  private static final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .version(HttpClient.Version.HTTP_1_1)
      .build();

  public static void main(String[] args) {
    long totalResponseTime = 0;
    long startTime = System.currentTimeMillis();
    System.out.println("Starting Single-Thread Client...");

    for (int i = 0; i < TOTAL_REQUESTS; i++) {

      long requestStart = System.nanoTime();
      String jsonBody = "{\"time\": 217, \"liftID\": 21}";
      String endpoint = SkierClientConfig.SERVER_URL + "1/seasons/2025/days/1/skiers/1";

      int responseCode = RequestSender.sendPostRequestWithRetry(httpClient, endpoint, jsonBody);
      long requestEnd = System.nanoTime();

      totalResponseTime += (requestEnd - requestStart);

      if (responseCode == 201) {
        successfulRequests.incrementAndGet();
      } else {
        failedRequests.incrementAndGet();
      }
    }
    long endTime = System.currentTimeMillis();
    long totalTime = endTime - startTime;
    double throughput = TOTAL_REQUESTS / (totalTime / 1000.0);
    double avgLatencySec = (totalResponseTime / (double) TOTAL_REQUESTS) / 1_000_000_000.0;

    System.out.println("\nSingle-Thread Client Performance:");
    System.out.println("====================================");
    System.out.println("Total successful requests: " + successfulRequests.get());
    System.out.println("Total failed requests: " + failedRequests.get());
    System.out.println("Total runtime: " + totalTime + " ms");
    System.out.printf("Throughput: %.2f requests/second%n", throughput);
    System.out.printf("Estimated Average Latency in seconds: %.2f s/request%n", avgLatencySec);
  }
}

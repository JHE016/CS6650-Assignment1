package api;

import static config.SkierClientConfig.MAX_RETRIES;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RequestSender {
  public static int sendPostRequestWithRetry(HttpClient client, String endpoint, String jsonBody) {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();

    int lastResponseCode = 500;

    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        lastResponseCode = response.statusCode(); // Store last received status code

        if (lastResponseCode == 201) {
          return 201; // Success
        }

        // Retry on 4XX (client error from servlet) and 5XX (server error)
        if (lastResponseCode >= 400 && lastResponseCode < 600) {
          System.err.printf("Request failed with status %d (attempt %d/%d)%n", lastResponseCode, attempt + 1, MAX_RETRIES);

          if (attempt < MAX_RETRIES - 1) {
            Thread.sleep(100 * (attempt + 1)); // Exponential backoff
          }
        } else {
          return lastResponseCode; // Do not retry for other response codes
        }

      } catch (Exception e) {
        System.err.printf("Request exception (attempt %d/%d): %s%n",
            attempt + 1, MAX_RETRIES, e.getMessage());

        if (attempt < MAX_RETRIES - 1) {
          try {
            Thread.sleep(100 * (attempt + 1));
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return 500; // Interrupted, return failure
          }
        }
      }
    }
    return lastResponseCode; // Return the last response code received after all retries
  }
}


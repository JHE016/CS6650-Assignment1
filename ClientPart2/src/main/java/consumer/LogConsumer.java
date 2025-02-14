package consumer;

import static config.SkierClientConfig.FILE_PATH;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import model.RequestLog;

public class LogConsumer implements Runnable {
  private final ConcurrentLinkedQueue<RequestLog> queue;

  public LogConsumer(ConcurrentLinkedQueue<RequestLog> queue) {
    this.queue = queue;
  }

  @Override
  public void run() {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH))) {
      writer.write("StartTime,RequestType,Latency(ms),ResponseCode\n");

      RequestLog log;
      while ((log = queue.poll()) != null) {
        writer.write(String.format("%d,%s,%d,%d\n",
            log.getStartTime(),
            log.getRequestType(),
            log.getLatency(),
            log.getResponseCode()));
      }
      System.out.println("Latency data saved to latency_data.csv");
    } catch (IOException e) {
      System.err.println("Error writing to file: " + e.getMessage());
    }
  }
}

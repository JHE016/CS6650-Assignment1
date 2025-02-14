package producer;

import config.SkierClientConfig;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import model.LiftRide;

public class SkierProducer implements Runnable {
  private final BlockingQueue<String> eventQueue;
  private final int totalRequests;
  private final Random random;

  public SkierProducer(BlockingQueue<String> eventQueue, int totalRequests) {
    this.eventQueue = eventQueue;
    this.totalRequests = totalRequests;
    this.random = new Random();
  }

  @Override
  public void run() {
    try {
      for (int i = 0; i < totalRequests; i++) {
        eventQueue.put(generateRandomEvent());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Event generation interrupted: " + e.getMessage());
    }
  }

  public String generateRandomEvent(){
    int skierID = random.nextInt(100000) + 1;
    int resortID = random.nextInt(10) + 1;
    int liftID = random.nextInt(40) + 1;
    int seasonID = 2025;
    int dayID = 1;
    int time = random.nextInt(360) + 1;

    LiftRide liftRide = new LiftRide(time, liftID);

    String endpoint = String.format(
        "%s/%d/seasons/%d/days/%d/skiers/%d",
        SkierClientConfig.SERVER_URL,
        resortID,
        seasonID,
        dayID,
        skierID
    );

    return endpoint + "::" + liftRide.toString();
  }
}
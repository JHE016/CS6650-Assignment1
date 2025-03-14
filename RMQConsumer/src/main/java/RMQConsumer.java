import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class RMQConsumer {
  private static final String QUEUE_NAME = "ski_lift_queue";
  private static final String RABBITMQ_HOST = "172.31.21.217";
  private static final int THREAD_POOL_SIZE = 40; // Adjust based on performance needs

  // Thread-safe map to store skier lift rides
  private static final ConcurrentHashMap<String, Integer> skierLiftRecords = new ConcurrentHashMap<>();

  public static void main(String[] args) {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(RABBITMQ_HOST);

    try {
      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.queueDeclare(QUEUE_NAME, true, false, false, null);
      System.out.println(" [*] Waiting for messages from RabbitMQ...");

      DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        processMessage(message);
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      };

      // Enable multiple consumers (multi-threaded)
      channel.basicQos(THREAD_POOL_SIZE);
      channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});

    } catch (IOException | TimeoutException e) {
      e.printStackTrace();
    }
  }

  private static void processMessage(String message) {
    try {
      JsonObject json = JsonParser.parseString(message).getAsJsonObject();
      String skierID = json.get("skierID").getAsString();
      int liftID = json.get("liftID").getAsInt();

      skierLiftRecords.merge(skierID, 1, Integer::sum); // Track rides per skier
      System.out.println("Processed ride for skier " + skierID + " on lift " + liftID);
    } catch (Exception e) {
      System.err.println("Error processing message: " + message);
    }
  }
}

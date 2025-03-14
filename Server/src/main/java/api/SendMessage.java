package api;

import com.rabbitmq.client.*;

public class SendMessage {
  private final static String QUEUE_NAME = "testQueue";

  public static void main(String[] argv) throws Exception {
    // Set up the connection and channel
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("54.203.218.195");
    factory.setUsername("admin");  // Set the new username you created
    factory.setPassword("admin");
    try (Connection connection = factory.newConnection();
        Channel channel = connection.createChannel()) {

      // Declare a queue
      boolean durable = true;  // Set this to 'true' if the queue was created as durable
      channel.queueDeclare("testQueue", durable, false, false, null);
      String message = "Hello RabbitMQ!";
      channel.basicPublish("", "testQueue", null, message.getBytes());
      System.out.println(" [x] Sent '" + message + "'");

      System.out.println("Queue declared successfully!");
    }
  }
}


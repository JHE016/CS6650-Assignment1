package api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import rmqpool.RMQChannelFactory;
import rmqpool.RMQChannelPool;

public class SkierServlet extends HttpServlet {
  private final Gson gson = new Gson();
  private static final int MAX_MINUTES_IN_DAY = 1440;
  private static final int MAX_DAYS_IN_YEAR = 366;
  private static final String YEAR_PATTERN = "\\d{4}";
  private static final String QUEUE_NAME = "ski_lift_queue";

  // RabbitMQ Connection & Channel Pool
  private static final String RABBITMQ_HOST = "54.203.218.195";
  private static final int CHANNEL_POOL_SIZE = 30;
  private Connection rabbitMQConnection;
  private RMQChannelPool channelPool;

  @Override
  public void init() throws ServletException {
    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost(RABBITMQ_HOST);
      factory.setUsername("admin");  // Set the new username you created
      factory.setPassword("admin");
      rabbitMQConnection = factory.newConnection();
      RMQChannelFactory channelFactory = new RMQChannelFactory(rabbitMQConnection);
      channelPool = new RMQChannelPool(CHANNEL_POOL_SIZE, channelFactory);
    } catch (Exception e) {
      throw new ServletException("Failed to initialize RabbitMQ connection", e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String[] pathParts = request.getPathInfo().split("/");

    if (pathParts.length != 8) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL path format");
      return;
    }

    String resortID = pathParts[1];
    String seasonID = pathParts[3];
    String dayID = pathParts[5];
    String skierID = pathParts[7];

    try {
      int resortIDInt = Integer.parseInt(resortID);
      if (resortIDInt <= 0) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Resort ID must be positive");
        return;
      }

      if (!seasonID.matches(YEAR_PATTERN)) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Season ID must be a 4-digit year");
        return;
      }

      int dayIDInt = Integer.parseInt(dayID);
      if (dayIDInt < 1 || dayIDInt > MAX_DAYS_IN_YEAR) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Day ID must be between 1 and 366");
        return;
      }

      int skierIDInt = Integer.parseInt(skierID);
      if (skierIDInt <= 0) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Skier ID must be positive");
        return;
      }
    } catch (NumberFormatException e) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid numeric parameter format");
      return;
    }

    // Read JSON body from request
    StringBuilder jsonBody = new StringBuilder();
    String line;
    try (BufferedReader reader = request.getReader()) {
      while ((line = reader.readLine()) != null) {
        jsonBody.append(line);
      }
    }

    if (jsonBody.toString().trim().isEmpty()) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Empty request body.");
      return;
    }

    JsonObject jsonObject;
    try {
      jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);
    } catch (JsonSyntaxException e) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format.");
      return;
    }

    if (!jsonObject.has("time") || jsonObject.get("time").isJsonNull() ||
        !jsonObject.has("liftID") || jsonObject.get("liftID").isJsonNull()) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing required fields: time and liftID.");
      return;
    }

    int time, liftID;
    try {
      time = jsonObject.get("time").getAsInt();
      liftID = jsonObject.get("liftID").getAsInt();
    } catch (UnsupportedOperationException | NumberFormatException e) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid time or liftID.");
      return;
    }

    if (time < 0 || time > MAX_MINUTES_IN_DAY) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Time must be between 0 and 1440");
      return;
    }

    if (liftID < 1) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Lift ID must be positive");
      return;
    }

    // Prepare message for RabbitMQ
    JsonObject message = new JsonObject();
    message.addProperty("resortID", resortID);
    message.addProperty("seasonID", seasonID);
    message.addProperty("dayID", dayID);
    message.addProperty("skierID", skierID);
    message.addProperty("time", time);
    message.addProperty("liftID", liftID);

    boolean sentSuccessfully = sendMessageToQueue(message.toString());

    if (!sentSuccessfully) {
      sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to send message to queue.");
      return;
    }

    response.setStatus(HttpServletResponse.SC_CREATED);
    response.setContentType("application/json");
    JsonObject successResponse = new JsonObject();
    successResponse.addProperty("message", "Lift ride recorded successfully");
    response.getWriter().write(gson.toJson(successResponse));
  }

  private boolean sendMessageToQueue(String message) {
    Channel channel = null;
    try {
      channel = channelPool.borrowObject();
      channel.queueDeclare(QUEUE_NAME, true, false, false, null);
      channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
      System.out.println("Message sent to queue: " + message);
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } finally {
      if (channel != null) {
        try {
          channelPool.returnObject(channel);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  @Override
  public void destroy() {
    try {
      if (rabbitMQConnection != null) {
        rabbitMQConnection.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
    response.setStatus(statusCode);
    response.setContentType("application/json");
    JsonObject errorResponse = new JsonObject();
    errorResponse.addProperty("error", message);
    response.getWriter().write(gson.toJson(errorResponse));
  }
}

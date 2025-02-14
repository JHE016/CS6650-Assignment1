package api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

public class SkierServlet extends HttpServlet {
  private final Gson gson = new Gson();
  private static final int MAX_MINUTES_IN_DAY = 1440;
  private static final int MAX_DAYS_IN_YEAR = 366;
  private static final String YEAR_PATTERN = "\\d{4}";

  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    res.setContentType("text/plain");
    String urlPath = req.getPathInfo();

    // check we have a URL!
    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("missing parameters");
      return;
    }

    String[] urlParts = urlPath.split("/");
    // and now validate url path and return the response status code
    // (and maybe also some value if input is valid)

    if (!isUrlValid(urlParts)) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
    } else {
      res.setStatus(HttpServletResponse.SC_OK);
      // do any sophisticated processing with urlParts which contains all the url params
      // TODO: process url params in `urlParts`
      res.getWriter().write("It works!");
    }
  }

  private boolean isUrlValid(String[] urlPath) {
    // TODO: validate the request url path according to the API spec
    // urlPath  = "/1/seasons/2019/day/1/skier/123"
    // urlParts = [, 1, seasons, 2019, day, 1, skier, 123]
    return true;
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    // Debug logging
    // System.out.println("Path Info: " + request.getPathInfo());
    // System.out.println("Context Path: " + request.getContextPath());
    // System.out.println("Servlet Path: " + request.getServletPath());

    // Extract path parameters from URL path
    String[] pathParts = request.getPathInfo().split("/");

    // Validate path has correct number of parts
    if (pathParts.length != 8) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL path format");
    }

    // Extract parameters from path
    String resortID = pathParts[1];     // 12
    String seasonID = pathParts[3];     // 2019
    String dayID = pathParts[5];        // 1
    String skierID = pathParts[7];      // 123

    // Validate path parameters
    if (resortID == null || seasonID == null || dayID == null || skierID == null) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing required path parameters.");
      return;
    }

    // Validate path parameters
    try {
      // Validate resortID
      int resortIDInt = Integer.parseInt(resortID);
      if (resortIDInt <= 0) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Resort ID must be a positive integer");
        return;
      }

      // Validate seasonID format (should be a 4-digit year)
      if (!seasonID.matches(YEAR_PATTERN)) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Season ID must be a 4-digit year");
        return;
      }

      // Validate dayID range (1-366)
      int dayIDInt = Integer.parseInt(dayID);
      if (dayIDInt < 1 || dayIDInt > MAX_DAYS_IN_YEAR) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Day ID must be between 1 and 366");
        return;
      }
      // Validate skierID
      int skierIDInt = Integer.parseInt(skierID);
      if (skierIDInt <= 0) {
        sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Skier ID must be a positive integer");
        return;
      }
    } catch (NumberFormatException e) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
          "Invalid numeric parameter format");
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

    // Check if request body is empty
    if (jsonBody.toString().trim().isEmpty()) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Empty request body.");
      return;
    }

    // Parse JSON data
    JsonObject jsonObject;
    try {
      jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);
    } catch (JsonSyntaxException e) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format.");
      return;
    }

    // Debugging: Print received JSON
    System.out.println("Received JSON: " + jsonBody);

    // Validate JSON object exists
    if (jsonObject == null) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid or missing JSON body.");
      return;
    }
  //  System.out.println("JSON Object type of time: " + jsonObject.get("time").getClass().getName());
  //  System.out.println("JSON Object type of liftID: " + jsonObject.get("liftID").getClass().getName());
  //  System.out.println("Raw JSON: " + jsonObject.toString());

    // Validate required fields exist and are not null
    if (!jsonObject.has("time") || jsonObject.get("time").isJsonNull() ||
        !jsonObject.has("liftID") || jsonObject.get("liftID").isJsonNull()) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing required fields: time and liftID.");
      return;
    }

    // Extract and validate fields
    int time, liftID;
    try {
      time = jsonObject.get("time").getAsInt();
      liftID = jsonObject.get("liftID").getAsInt();
    } catch (UnsupportedOperationException | NumberFormatException e) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid time or liftID. They must be integers.");
      return;
    }

    // Add range validation for time and liftID
    if (time < 0 || time > MAX_MINUTES_IN_DAY) { // Assuming time is in minutes of day
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
          "time must be between 0 and 1440");
      return;
    }

    if (liftID < 1) {
      sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
          "liftID must be a positive integer");
      return;
    }

    // Debugging: Print extracted values
    System.out.println("Parsed values - Time: " + time + ", LiftID: " + liftID);

    // Prepare success response
    response.setStatus(HttpServletResponse.SC_CREATED); // 201 Created
    response.setContentType("application/json");
    JsonObject successResponse = new JsonObject();
    successResponse.addProperty("message", "Lift ride recorded successfully");
    response.getWriter().write(gson.toJson(successResponse));
  }

  /**
   * Helper method to send JSON error responses.
   */
  private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
    response.setStatus(statusCode);
    response.setContentType("application/json");
    JsonObject errorResponse = new JsonObject();
    errorResponse.addProperty("error", message);
    response.getWriter().write(gson.toJson(errorResponse));
  }
}
package model;

/**
 * Represents a record containing request latency data for logging and analysis.
 */
public class RequestLog {
  private final long startTime;
  private final String requestType;
  private final long latency;
  private final int responseCode;

  public RequestLog(long startTime, String requestType, long latency, int responseCode) {
    this.startTime = startTime;
    this.requestType = requestType;
    this.latency = latency;
    this.responseCode = responseCode;
  }

  public long getStartTime() {
    return startTime;
  }

  public String getRequestType() {
    return requestType;
  }

  public long getLatency() {
    return latency;
  }

  public int getResponseCode() {
    return responseCode;
  }

  @Override
  public String toString() {
    return startTime + "," + requestType + "," + latency + "," + responseCode;
  }
}

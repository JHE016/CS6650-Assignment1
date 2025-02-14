package config;
public class SkierClientConfig {
  // Server URL
  public static final String SERVER_URL = "http://44.245.120.39:8080/Assignment1_war/skiers/";
  //  public static final String SERVER_URL = "http://localhost:8080/Assignment1/skiers/";

  public static final String FILE_PATH = "latency_data.csv";

  // Thread Configuration
  public static final int INITIAL_THREADS = 32;
  public static final int SECOND_PHASE_THREADS = 200;

  // Request Configuration
  public static final int INITIAL_REQUESTS_PER_THREAD = 1000;
  public static final int TOTAL_REQUESTS = 200000;

  // Retry and Queue Configuration
  public static final int MAX_RETRIES = 5;
  public static final int QUEUE_SIZE = 10000;

}
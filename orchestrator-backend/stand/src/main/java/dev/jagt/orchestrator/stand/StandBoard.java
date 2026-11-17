package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class StandBoard {

  private final HttpClient http =
      HttpClient.newBuilder()
          .cookieHandler(new CookieManager())
          .connectTimeout(Duration.ofSeconds(30))
          .build();
  private final HttpClient bare =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
  private final ObjectMapper mapper = JsonMapper.builder().build();
  private static final Duration READING = Duration.ofSeconds(60);
  private static final Duration OPENING = Duration.ofMinutes(10);

  private final String base;
  private final String password;
  private boolean loggedIn;

  public StandBoard(int port) {
    this("http://127.0.0.1:" + port, "");
  }

  public StandBoard(String base, String password) {
    this.base = base;
    this.password = password;
  }

  public boolean ready() {
    try {
      return send(HttpRequest.newBuilder(URI.create(base + "/state")).timeout(READING).GET())
              .statusCode()
          == 200;
    } catch (StandFailure down) {
      return false;
    }
  }

  public JsonNode open(String ref, String project, @Nullable String mode) {
    String body =
        mapper
            .createObjectNode()
            .put("ref", ref)
            .put("project", project)
            .put("mode", mode)
            .toString();
    return post("/api/tasks", body, OPENING);
  }

  public JsonNode press(String task, String verb) {
    return post("/api/tasks/actions/" + verb + "?task=" + task, "{}", READING);
  }

  public void run(String job) {
    try {
      post("/api/commands/run?about=" + job, "{}", READING);
    } catch (StandFailure running) {
      return;
    }
  }

  public JsonNode say(String task, String line) {
    return post(
        "/api/tasks/say?task=" + task,
        mapper.createObjectNode().put("line", line).toString(),
        READING);
  }

  public JsonNode create(String project, String notes) {
    String body = mapper.createObjectNode().put("project", project).put("notes", notes).toString();
    return post("/api/tasks", body, OPENING);
  }

  public String command(String path) {
    return postText(path, "{}", READING);
  }

  public int status(String path) {
    return send(HttpRequest.newBuilder(URI.create(base + path)).timeout(READING).GET())
        .statusCode();
  }

  public String text(String path) {
    return send(HttpRequest.newBuilder(URI.create(base + path)).timeout(READING).GET()).body();
  }

  public int anonymously(String path, Map<String, String> headers, @Nullable String body) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(base + path)).header("Origin", base).timeout(READING);
    headers.forEach(request::header);
    HttpRequest.BodyPublisher sent =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
    return transmit(request.method(body == null ? "GET" : "POST", sent), bare).statusCode();
  }

  private JsonNode post(String path, String body, Duration timeout) {
    return mapper.readTree(postText(path, body, timeout));
  }

  private String postText(String path, String body, Duration timeout) {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .header("Origin", base)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    if (response.statusCode() != 200) {
      throw new StandFailure(path + " answered " + response.statusCode() + ": " + response.body());
    }
    return response.body();
  }

  public JsonNode state() {
    return get("/state");
  }

  public JsonNode tasks() {
    return get("/api/tasks");
  }

  private JsonNode get(String path) {
    return mapper.readTree(
        send(HttpRequest.newBuilder(URI.create(base + path)).timeout(READING).GET()).body());
  }

  private HttpResponse<String> send(HttpRequest.Builder request) {
    login();
    HttpResponse<String> response = transmit(request);
    boolean unauthenticated = response.statusCode() == 401 || response.statusCode() / 100 == 3;
    if (unauthenticated && !password.isEmpty()) {
      forget();
      login();
      return transmit(request);
    }
    return response;
  }

  private synchronized void forget() {
    loggedIn = false;
  }

  private synchronized void login() {
    if (password.isEmpty() || loggedIn) {
      return;
    }
    HttpResponse<String> response =
        transmit(
            HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", base)
                .timeout(READING)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username=owner&password="
                            + URLEncoder.encode(password, StandardCharsets.UTF_8))));
    if (response.statusCode() >= 400) {
      throw new StandFailure("login at " + base + " answered " + response.statusCode());
    }
    loggedIn = true;
  }

  private HttpResponse<String> transmit(HttpRequest.Builder request) {
    return transmit(request, http);
  }

  private HttpResponse<String> transmit(HttpRequest.Builder request, HttpClient client) {
    try {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new StandFailure("jagt at " + base + " did not answer", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StandFailure("interrupted waiting for jagt at " + base, e);
    }
  }
}

package dev.jagt.orchestrator.eval;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public final class AcceptanceFixtureEnvironment implements AutoCloseable {

  private static final JsonMapper MAPPER = new JsonMapper();

  private final HttpServer server;
  private final Map<String, Reply> replies = new HashMap<>();

  private record Reply(int status, String contentType, String body) {}

  @SuppressWarnings("unchecked")
  private AcceptanceFixtureEnvironment(Map<String, Object> environment) throws IOException {
    ((Map<String, Object>) environment.getOrDefault("login", Map.of()))
        .forEach(
            (role, said) ->
                replies.put(
                    "GET /login/" + role,
                    "ok".equals(said)
                        ? new Reply(200, "text/plain", "signed in as " + role)
                        : new Reply(401, "text/plain", String.valueOf(said))));
    ((Map<String, Object>) environment.getOrDefault("http", Map.of()))
        .forEach((call, said) -> replies.put(call, http((Map<String, Object>) said)));
    ((Map<String, Object>) environment.getOrDefault("ui", Map.of()))
        .forEach(
            (page, said) ->
                replies.put("GET " + page, new Reply(200, "application/json", json(said))));
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::answer);
    server.start();
  }

  public static AcceptanceFixtureEnvironment serve(Map<String, Object> environment) {
    try {
      return new AcceptanceFixtureEnvironment(environment);
    } catch (IOException unbound) {
      throw new UncheckedIOException(unbound);
    }
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private static Reply http(Map<String, Object> recorded) {
    int status = recorded.get("status") instanceof Number number ? number.intValue() : 200;
    String contentType =
        recorded.get("contentType") instanceof String type ? type : "application/json";
    Object body = recorded.get("body");
    if (body == null) {
      Map<String, Object> rest = new HashMap<>(recorded);
      rest.remove("status");
      rest.remove("contentType");
      return new Reply(status, contentType, rest.isEmpty() ? "" : json(rest));
    }
    return new Reply(status, contentType, body instanceof String text ? text : json(body));
  }

  private static String json(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JacksonException unwritable) {
      throw new IllegalArgumentException("fixture not serialisable: " + unwritable, unwritable);
    }
  }

  private void answer(HttpExchange exchange) throws IOException {
    Reply reply =
        replies.getOrDefault(
            exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(),
            new Reply(404, "text/plain", "no such page"));
    byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", reply.contentType());
    exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      exchange.getResponseBody().write(body);
    }
    exchange.close();
  }
}

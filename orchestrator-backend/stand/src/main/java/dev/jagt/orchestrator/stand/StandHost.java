package dev.jagt.orchestrator.stand;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

public class StandHost implements AutoCloseable {

  private final HttpServer server;
  private final StandStore store;
  private final JsonRpc rpc;

  private StandHost(HttpServer server, StandStore store, JsonRpc rpc) {
    this.server = server;
    this.store = store;
    this.rpc = rpc;
    server.createContext("/mcp", this::serve);
    server.createContext("/app", this::page);
  }

  public static StandHost listen(Path directory, InetAddress bind) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(bind, 0), 0);
    StandStore store =
        new StandStore(directory, "http://127.0.0.1:" + server.getAddress().getPort());
    return new StandHost(server, store, new JsonRpc(new StandTools(store)));
  }

  public StandHost start() {
    server.start();
    return this;
  }

  public String mcpUrl(String host) {
    return "http://" + host + ":" + server.getAddress().getPort() + "/mcp";
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public StandStore store() {
    return store;
  }

  @Override
  public void close() {
    server.stop(0);
  }

  public String appUrl(String host) {
    return "http://" + host + ":" + server.getAddress().getPort() + "/app";
  }

  private void page(HttpExchange exchange) throws IOException {
    try (exchange) {
      byte[] bytes = "<h1>Hello, World</h1>".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
    }
  }

  private void serve(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!exchange.getRequestMethod().equals("POST")) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      Optional<String> answer = rpc.handle(body);
      if (answer.isEmpty()) {
        exchange.sendResponseHeaders(202, -1);
        return;
      }
      byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
    }
  }
}

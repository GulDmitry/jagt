package dev.jagt.orchestrator.stand;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class StandHost implements AutoCloseable {

  private final HttpServer server;
  private final StandStore store;
  private final JsonRpc rpc;
  private final FakeGh gh;

  private StandHost(HttpServer server, StandStore store, JsonRpc rpc) {
    this.server = server;
    this.store = store;
    this.rpc = rpc;
    this.gh = new FakeGh(store);
    server.createContext("/mcp", this::serve);
    server.createContext("/gh", this::serveGh);
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

  public void afterPost(Runnable hook) {
    gh.afterPost(hook);
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

  private void serveGh(HttpExchange exchange) throws IOException {
    try (exchange) {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      List<String> args = new ArrayList<>(List.of(body.split("\0", -1)));
      args.removeLast();
      int status = 200;
      String printed;
      try {
        printed = gh.run(args);
      } catch (StandFailure refused) {
        status = 400;
        printed = Objects.requireNonNullElse(refused.getMessage(), "refused");
      }
      byte[] bytes = printed.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
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

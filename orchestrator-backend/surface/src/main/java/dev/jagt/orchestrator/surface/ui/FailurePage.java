package dev.jagt.orchestrator.surface.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;

/** Stands in for the board while a start is refused. */
@Slf4j
public final class FailurePage implements ServedFailure {

  private static final String HEAD =
      """
      <!doctype html><meta charset="utf-8"><title>jagt cannot start</title>
      <body style="font:15px system-ui;margin:2em auto;max-width:60em;padding:0 1em">
      <h1>jagt cannot start</h1>
      <pre style="white-space:pre-wrap">
      """;

  private static final String FORM =
      """
      </pre>
      <form method="post" action="/save">
      <textarea name="yml" rows="30" style="width:100%;font:13px ui-monospace,monospace">
      """;

  private static final String FOOT =
      """
      </textarea>
      <p><button>Save jagt.yml and start again</button></p>
      </form>
      </body>
      """;

  private final HttpServer server;
  private final Path configFile;
  private final String problems;
  private final CountDownLatch saved = new CountDownLatch(1);

  private FailurePage(HttpServer server, Path configFile, String problems) {
    this.server = server;
    this.configFile = configFile;
    this.problems = problems;
  }

  /** Empty where the port cannot be bound. */
  public static Optional<FailurePage> listen(int port, Path configFile, String problems) {
    try {
      HttpServer server =
          HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
      FailurePage page = new FailurePage(server, configFile, problems);
      server.createContext("/", page::handle);
      server.start();
      return Optional.of(page);
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  @Override
  public int port() {
    return server.getAddress().getPort();
  }

  @Override
  public void awaitSave() throws InterruptedException {
    saved.await();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!ours(exchange)) {
        reply(exchange, 403, "refused");
      } else if ("POST".equals(exchange.getRequestMethod())) {
        save(exchange);
      } else {
        reply(exchange, 200, HEAD + escaped(problems) + FORM + escaped(current()) + FOOT);
      }
    }
  }

  private void save(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    String yml =
        URLDecoder.decode(body.replaceFirst("^yml=", ""), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    if (Files.exists(configFile)) {
      Files.copy(
          configFile,
          configFile.resolveSibling(configFile.getFileName() + ".bak"),
          StandardCopyOption.REPLACE_EXISTING);
    }
    Files.writeString(configFile, yml);
    log.atInfo().setMessage("failure page saved").addKeyValue("file", configFile).log();
    reply(exchange, 200, "<!doctype html><meta charset=\"utf-8\">Saved: starting again.");
    exchange.close();
    saved.countDown();
  }

  private boolean ours(HttpExchange exchange) {
    String host = exchange.getRequestHeaders().getFirst("Host");
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    boolean loopback = ("127.0.0.1:" + port()).equals(host) || ("localhost:" + port()).equals(host);
    return loopback && (origin == null || ("http://" + host).equals(origin));
  }

  private String current() throws IOException {
    Path dist = configFile.resolveSibling(configFile.getFileName() + ".dist");
    Path source = Files.exists(configFile) ? configFile : dist;
    return Files.exists(source) ? Files.readString(source) : "";
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  private static String escaped(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}

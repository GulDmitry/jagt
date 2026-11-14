package dev.jagt.orchestrator.adapter.assistant.reach;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

final class FakeModelServer {

  private final HttpServer server;

  FakeModelServer(AtomicReference<String> body) throws IOException {
    server =
        HttpServer.create(
            new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
    server.createContext(
        "/v1/models",
        exchange -> {
          byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  int port() {
    return server.getAddress().getPort();
  }

  void stop() {
    server.stop(0);
  }
}

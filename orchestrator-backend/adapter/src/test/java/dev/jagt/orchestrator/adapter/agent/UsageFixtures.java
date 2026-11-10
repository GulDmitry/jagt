package dev.jagt.orchestrator.adapter.agent;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

final class UsageFixtures {

  private UsageFixtures() {}

  static UsageEndpoint endpointAtNoon() {
    return new UsageEndpoint(
        HttpClient.newHttpClient(),
        Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));
  }

  static URI usageUriOf(HttpServer server) {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/usage");
  }
}

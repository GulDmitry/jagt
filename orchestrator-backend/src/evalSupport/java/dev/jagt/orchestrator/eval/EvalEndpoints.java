package dev.jagt.orchestrator.eval;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class EvalEndpoints {

  private EvalEndpoints() {}

  public static void register(DynamicPropertyRegistry registry, int gatePort, int deadPort) {
    registry.add("server.port", () -> gatePort);
    registry.add("orchestrator.mcp-url", () -> "http://127.0.0.1:" + deadPort + "/mcp");
    registry.add(
        "orchestrator.hook-url", () -> "http://127.0.0.1:" + deadPort + "/api/agent/session");
    registry.add("orchestrator.gate-url", () -> "http://127.0.0.1:" + gatePort + "/api/agent");
  }
}

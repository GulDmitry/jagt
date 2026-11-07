package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.ToolGateEnvelope;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Whatever agent runs the tasks: the headless reads stay Claude, and their read gate answers in
 * this shape.
 */
@Component
class ClaudeToolGateEnvelope implements ToolGateEnvelope {

  @Override
  public Kind kindOf(String toolName) {
    return switch (toolName.toLowerCase(Locale.ROOT)) {
      case "bash" -> Kind.SHELL;
      case "edit", "write", "multiedit", "notebookedit" -> Kind.FILE_WRITE;
      default -> Kind.OTHER;
    };
  }

  @Override
  public Map<String, Object> denial(String reason) {
    return Map.of(
        "continue",
        true,
        "hookSpecificOutput",
        Map.of(
            "hookEventName", "PreToolUse",
            "permissionDecision", "deny",
            "permissionDecisionReason", reason));
  }
}

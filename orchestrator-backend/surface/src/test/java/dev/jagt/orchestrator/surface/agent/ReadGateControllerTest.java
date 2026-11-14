package dev.jagt.orchestrator.surface.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.ToolGateEnvelope;
import dev.jagt.orchestrator.service.gate.ReadScopes.ReadScope;
import dev.jagt.orchestrator.service.gate.ReadScopes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadGateControllerTest {

  private final ToolGateEnvelope envelope = mock(ToolGateEnvelope.class);

  ReadGateControllerTest() {
    when(envelope.denial(any())).thenReturn(Map.of("hookSpecificOutput", "deny"));
  }

  @Test
  void refusesCallUnderFenceNoReadHolds() {
    var answered =
        new ReadGateController(new ReadScopes(), envelope)
            .gate(
                "gone", new ReadGateController.ToolCall("mcp__acme__get_issue", Map.of(), "/tmp"));

    assertThat(answered.getBody())
        .extractingByKey("hookSpecificOutput")
        .asString()
        .contains("deny");
  }

  @Test
  void refusesToolTheHeldReadWasNotGiven() {
    ReadScopes scopes = new ReadScopes();
    scopes.open("f1", new ReadScope(List.of(), List.of("mcp__acme__get*"), false));

    var answered =
        new ReadGateController(scopes, envelope)
            .gate("f1", new ReadGateController.ToolCall("WebFetch", Map.of(), "/tmp"));

    assertThat(answered.getBody())
        .extractingByKey("hookSpecificOutput")
        .asString()
        .contains("deny");
  }

  @Test
  void marksTheFenceRefusedOnceToolIsRefusedUnderIt() {
    ReadScopes scopes = new ReadScopes();
    scopes.open("f1", new ReadScope(List.of(), List.of("mcp__acme__get*"), false));

    new ReadGateController(scopes, envelope)
        .gate("f1", new ReadGateController.ToolCall("WebFetch", Map.of(), "/tmp"));

    assertThat(scopes.refused("f1")).isTrue();
  }

  @Test
  void answersToolTheReadWasGivenWithNothing() {
    ReadScopes scopes = new ReadScopes();
    scopes.open("f1", new ReadScope(List.of(), List.of("mcp__acme__get*"), false));

    var answered =
        new ReadGateController(scopes, envelope)
            .gate("f1", new ReadGateController.ToolCall("mcp__acme__get_issue", Map.of(), "/tmp"));

    assertThat(answered.getBody()).isNull();
  }

  @Test
  void marksTheFenceAsHeardOnceCallAsksUnderIt() {
    ReadScopes scopes = new ReadScopes();
    scopes.open("f1", new ReadScope(List.of(), List.of("StructuredOutput"), false));

    new ReadGateController(scopes, envelope)
        .gate("f1", new ReadGateController.ToolCall("StructuredOutput", Map.of(), "/tmp"));

    assertThat(scopes.close("f1")).isTrue();
  }

  @Test
  void keepsEachCallItJudgesSoTheReadsSourcesAreTheRunsOwn() {
    ReadScopes scopes = new ReadScopes();
    scopes.open("f1", new ReadScope(List.of(), List.of("mcp__acme__get*"), false));

    new ReadGateController(scopes, envelope)
        .gate(
            "f1",
            new ReadGateController.ToolCall(
                "mcp__acme__get_issue", Map.of("issueIdOrKey", "ABC-42"), "/tmp"));

    assertThat(scopes.heard("f1")).containsExactly("mcp__acme__get_issue {issueIdOrKey=ABC-42}");
  }
}

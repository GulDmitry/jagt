package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.service.ReadScopes;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadGateControllerTest {

    @Test
    void refusesACallUnderAFenceNoReadHolds() {
        var answered = new ReadGateController(new ReadScopes()).gate("gone",
                new ReadGateController.ToolCall("mcp__acme__get_issue", Map.of(), "/tmp"));

        assertThat(answered.getBody()).extractingByKey("hookSpecificOutput").asString().contains("deny");
    }

    @Test
    void answersAToolTheReadWasGivenWithNothing() {
        ReadScopes scopes = new ReadScopes();
        scopes.open("f1", new ReadScope(List.of(), List.of("mcp__acme__get*"), false));

        var answered = new ReadGateController(scopes).gate("f1",
                new ReadGateController.ToolCall("mcp__acme__get_issue", Map.of(), "/tmp"));

        assertThat(answered.getBody()).isNull();
    }

    @Test
    void marksTheFenceAsHeardOnceACallAsksUnderIt() {
        ReadScopes scopes = new ReadScopes();
        scopes.open("f1", new ReadScope(List.of(), List.of("StructuredOutput"), false));

        new ReadGateController(scopes).gate("f1", new ReadGateController.ToolCall("StructuredOutput", Map.of(), "/tmp"));

        assertThat(scopes.close("f1")).isTrue();
    }
}

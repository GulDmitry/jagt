package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.assistant.OpenCodeEvents.Call;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeToolCallsTest {

    private final JsonMapper json = new JsonMapper();

    @Test
    void namesTheFileAReadOpenedThatTheRoleRefuses(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of("Read(**/review_replies.md)"), false, false,
                List.of(worktree), List.of());
        Call call = new Call("read", "completed", json.createObjectNode().put("filePath",
                worktree.resolve("review_replies.md").toString()));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).hasValue("read " + worktree.resolve("review_replies.md"));
    }

    @Test
    void keepsAnAnswerThatReadAFileTheRoleDoesNotRefuseBesideOneItDoes(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of("Read(**/review_replies.md)"), false, false,
                List.of(worktree), List.of());
        Call call = new Call("read", "completed", json.createObjectNode().put("filePath", worktree.resolve("A.java").toString()));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isEmpty();
    }

    @Test
    void keepsAnAnswerWhoseRefusedReadWasDeniedAndSoNeverRan(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of("Read(**/review_replies.md)"), false, false,
                List.of(worktree), List.of());
        Call call = new Call("read", "error", json.createObjectNode().put("filePath",
                worktree.resolve("review_replies.md").toString()));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isEmpty();
    }
}

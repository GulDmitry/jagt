package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.assistant.OpenCodeEvents.Call;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeToolCallsTest {

    private final JsonMapper json = new JsonMapper();

    @Test
    void discardsAnAnswerWhoseCommandMatchesARefusedGlobThoughItMatchesAnAllowedOne(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of("Bash(git log:*)"), List.of("Bash(git * --ou*)"), false,
                List.of(worktree), List.of());
        Call call = new Call("shell", "completed", json.createObjectNode().put("command", "git log --output=out.txt"));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isPresent();
    }

    @Test
    void discardsAnAnswerWhoseCommandMatchesNoAllowedGlob(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, List.of(worktree), List.of());
        Call call = new Call("shell", "completed", json.createObjectNode().put("command", "touch out.txt"));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isPresent();
    }

    @Test
    void keepsAnAnswerWhoseCommandMatchesAnAllowedGlobAndNoRefusedOne(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of("Bash(git log:*)"), List.of("Bash(git * --ou*)"), false,
                List.of(worktree), List.of());
        Call call = new Call("shell", "completed", json.createObjectNode().put("command", "git log --oneline"));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isEmpty();
    }

    @Test
    void discardsAnAnswerWhoseRedirectPassedTheGlobThatAllowedItsCommand(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, List.of(worktree), List.of());
        Call call = new Call("shell", "completed", json.createObjectNode().put("command", "git log > f"));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).hasValueSatisfying(said -> assertThat(said).contains("git log > f"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"git log; touch f", "git log && touch f", "git log || touch f", "git log $(touch f)",
            "git log `touch f`", "git log\ntouch f"})
    void discardsAnAnswerWhoseChainedCommandRanBehindTheGlobThatAllowedItsFirst(String command, @TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, List.of(worktree), List.of());
        Call call = new Call("shell", "completed", json.createObjectNode().put("command", command));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isPresent();
    }

    @Test
    void discardsAnAnswerThatReadAFileOutsideTheWorktrees(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), false, List.of(worktree), List.of());
        Call call = new Call("read", "completed", json.createObjectNode().put("filePath", "/etc/hosts"));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isPresent();
    }

    @Test
    void keepsAnAnswerThatReadAFileInsideAnyOfTheWorktrees(@TempDir Path first, @TempDir Path second) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), false, List.of(first, second), List.of());
        Call call = new Call("read", "completed", json.createObjectNode().put("filePath", second.resolve("A.java").toString()));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isEmpty();
    }

    @Test
    void discardsAnAnswerThatReadAFileTheRoleRefuses(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of("Read(**/review_replies.md)"), false, List.of(worktree),
                List.of());
        Call call = new Call("read", "completed", json.createObjectNode().put("filePath",
                worktree.resolve("review_replies.md").toString()));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isPresent();
    }

    @Test
    void keepsAnAnswerWhoseOutOfScopeCallWasDeniedAndSoNeverRan(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of("Bash(git log:*)"), List.of(), false, List.of(worktree), List.of());
        Call call = new Call("shell", "error", json.createObjectNode().put("command", "touch b.txt"));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isEmpty();
    }

    @Test
    void discardsAnAnswerThatFetchedOutsideTheEnvironmentsTheRunNames(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), true, List.of(worktree),
                List.of("https://dev.example.test"));
        Call call = new Call("webfetch", "completed", json.createObjectNode().put("url", "https://other.example.test/x"));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isPresent();
    }

    @Test
    void keepsAnAnswerThatFetchedBelowAnEnvironmentTheRunNames(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), true, List.of(worktree),
                List.of("https://dev.example.test"));
        Call call = new Call("webfetch", "completed", json.createObjectNode().put("url", "https://dev.example.test/version"));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).isEmpty();
    }

    @Test
    void discardsAnAnswerThatCalledAToolTheReadWasNeverGiven(@TempDir Path worktree) {
        OpenCodeScope scope = OpenCodeScope.of(List.of(), List.of(), false, List.of(worktree), List.of());
        Call call = new Call("edit", "completed", json.createObjectNode().put("filePath", worktree.resolve("A.java").toString()));

        assertThat(OpenCodeToolCalls.violation(scope, List.of(call))).hasValueSatisfying(said -> assertThat(said).contains("edit"));
    }
}

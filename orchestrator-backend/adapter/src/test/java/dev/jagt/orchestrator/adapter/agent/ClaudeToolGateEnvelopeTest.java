package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.ToolGateEnvelope.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeToolGateEnvelopeTest {

    private final ClaudeToolGateEnvelope envelope = new ClaudeToolGateEnvelope();

    @ParameterizedTest
    @ValueSource(strings = {"Bash", "bash"})
    void readsTheShellToolAsAShell(String tool) {
        assertThat(envelope.kindOf(tool)).isEqualTo(Kind.SHELL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Edit", "Write", "MultiEdit", "NotebookEdit"})
    void readsTheFileToolsAsFileWrites(String tool) {
        assertThat(envelope.kindOf(tool)).isEqualTo(Kind.FILE_WRITE);
    }

    @Test
    void readsAnyOtherToolAsOther() {
        assertThat(envelope.kindOf("Read")).isEqualTo(Kind.OTHER);
    }

    @Test
    void deniesInTheShapeClaudesPreToolUseHookReads() {
        assertThat(envelope.denial("plan first")).isEqualTo(Map.of("continue", true,
                "hookSpecificOutput", Map.of("hookEventName", "PreToolUse", "permissionDecision", "deny",
                        "permissionDecisionReason", "plan first")));
    }

    @Test
    void isOfferedWhenAnotherAgentRunsTheTasksForTheHeadlessReadsStayClaude() {
        new ApplicationContextRunner().withUserConfiguration(ClaudeToolGateEnvelope.class)
                .withPropertyValues("orchestrator.agent.cli=codex")
                .run(context -> assertThat(context).hasSingleBean(ClaudeToolGateEnvelope.class));
    }
}

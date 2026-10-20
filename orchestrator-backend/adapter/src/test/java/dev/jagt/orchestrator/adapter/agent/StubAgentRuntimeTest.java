package dev.jagt.orchestrator.adapter.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StubAgentRuntimeTest {

    @Test
    void launchesAnImmediatelyExitingCommandWhereNoScriptIsGiven() {
        var runtime = new StubAgentRuntime(StubAgentProperties.defaults());

        assertThat(runtime.launchCommand(Path.of("/w/task"), false)).isEqualTo("true");
    }

    @Test
    void launchesTheScriptWithTheWorktreeQuoted() {
        var runtime = new StubAgentRuntime(new StubAgentProperties("/s/it's.sh"));

        assertThat(runtime.launchCommand(Path.of("/w/task"), false)).isEqualTo("'/s/it'\\''s.sh' '/w/task'");
    }

    @Test
    void appendsPlanToTheScriptInPlanMode() {
        var runtime = new StubAgentRuntime(new StubAgentProperties("/s/run.sh"));

        assertThat(runtime.launchCommand(Path.of("/w/task"), true)).isEqualTo("'/s/run.sh' '/w/task' plan");
    }

    @Test
    void treatsABlankScriptAsNoneAndStripsAnyOther() {
        assertThat(new StubAgentProperties("  ").script()).isNull();
        assertThat(new StubAgentProperties(" /s/run.sh ").script()).isEqualTo("/s/run.sh");
    }

    @Test
    void hearsNothingFromAScriptedSessionAndKnowsOfNoActivity() {
        var runtime = new StubAgentRuntime(StubAgentProperties.defaults());

        assertThat(runtime.humanSaid(Path.of("/w/task"), Set.of())).contains(List.of());
        assertThat(runtime.readsWhatTheHumanTyped()).isTrue();
        assertThat(runtime.lastSessionActivity(Path.of("/w/task"))).isEmpty();
    }

    @Test
    void briefsTheAgentThroughTheSharedKnowledgeFileInItsWorktree() {
        var runtime = new StubAgentRuntime(StubAgentProperties.defaults());

        assertThat(runtime.systemKnowledgeFile(Path.of("/w/does-not-exist")).getParent())
                .isEqualTo(Path.of("/w/does-not-exist"));
    }
}

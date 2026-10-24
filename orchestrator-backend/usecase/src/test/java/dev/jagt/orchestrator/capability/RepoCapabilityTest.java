package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Capabilities;
import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepoCapabilityTest {

    private final RepoAttachment attachment = mock(RepoAttachment.class);

    @Test
    void handsTheSessionBackToWorkOnceTheRepositoryIsAddedAndTheSessionTold() {
        when(attachment.attach("ABC-7")).thenReturn("add-repo ABC-7: registry is at /wt; the session was told");

        Outcome outcome = new RepoCapability(attachment).run("ABC-7");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
    }

    @Test
    void refusesWithTheCauseOnTheCardWhenTheWorktreeCouldNotBeCut() {
        when(attachment.attach("ABC-7")).thenThrow(new IllegalStateException("git: fetch failed"));

        Outcome outcome = new RepoCapability(attachment).run("ABC-7");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
        assertThat(outcome.stamp()).isEqualTo("add-repo failed: git: fetch failed");
        assertThat(outcome.stampMeans()).isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void leavesTheRequestStandingAndTheCardAskingWhenThePressFails(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties
                .defaults().withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-7", TaskState.builder("api", "/wt", TaskStatus.IN_PROGRESS).alias("a1")
                .message("needs the registry").report(AgentReport.QUESTION).repoRequest("registry").build());
        when(attachment.attach("ABC-7")).thenThrow(new IllegalStateException("git: fetch failed"));
        FlowEngine engine = new FlowEngine(state, new Capabilities(List.of(new RepoCapability(attachment)), List.of()),
                mock(AgentPresence.class), Clock.systemUTC());

        assertThatThrownBy(() -> engine.run("ABC-7", TaskAction.ADD_REPO)).isInstanceOf(IllegalStateException.class);

        TaskState task = state.task("ABC-7").orElseThrow();
        assertThat(task.repoRequest()).isEqualTo("registry");
        assertThat(task.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.message()).isEqualTo("add-repo failed: git: fetch failed");
        assertThat(task.report()).isEqualTo(AgentReport.QUESTION);
    }

    @Test
    void putsTheTaskBackToWorkWhenThePressSucceeds(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties
                .defaults().withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-7", TaskState.builder("api", "/wt", TaskStatus.CI_FAILED).alias("a1")
                .message("needs the registry").report(AgentReport.QUESTION).repoRequest("registry").build());
        when(attachment.attach("ABC-7")).thenReturn("add-repo ABC-7: registry is at /wt2; the session was told");
        FlowEngine engine = new FlowEngine(state, new Capabilities(List.of(new RepoCapability(attachment)), List.of()),
                mock(AgentPresence.class), Clock.systemUTC());

        engine.run("ABC-7", TaskAction.ADD_REPO);

        assertThat(state.task("ABC-7").orElseThrow().status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }
}

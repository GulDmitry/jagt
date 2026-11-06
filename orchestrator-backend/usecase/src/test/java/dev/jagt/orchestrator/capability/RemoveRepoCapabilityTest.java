package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Capabilities;
import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskRepo;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RemoveRepoCapabilityTest {

    private final RepoRemoval removal = mock(RepoRemoval.class);

    @Test
    void keepsTheTaskMessageAndTellsTheHumanWhenTheRepositoryLeft() {
        when(removal.remove("ABC-7", "web")).thenReturn("remove-repo ABC-7: web left the task");

        Outcome outcome = new RemoveRepoCapability(removal).run("ABC-7", "web");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.RELAYED);
        assertThat(outcome.message()).isEqualTo("remove-repo ABC-7: web left the task");
    }

    @Test
    void refusesWithTheCauseAndWritesNoStampWhenTheCloseFailed() {
        IllegalStateException refused = new IllegalStateException("the host did not close the request of web: no permission");
        when(removal.remove("ABC-7", "web")).thenThrow(refused);

        Outcome outcome = new RemoveRepoCapability(removal).run("ABC-7", "web");

        assertThat(outcome.kind()).isEqualTo(Outcome.Kind.PARTIAL);
        assertThat(outcome.message()).isEqualTo("remove-repo ABC-7 failed: the host did not close the request of web: no permission");
        assertThat(outcome.stamp()).isNull();
        assertThat(outcome.cause()).isSameAs(refused);
    }

    @Test
    void refusesARemovalThatNamesNoProject() {
        assertThatThrownBy(() -> new RemoveRepoCapability(removal).run("ABC-7"))
                .isInstanceOf(Refusal.class).hasMessageContaining("remove-repo <ticket> <project>");
    }

    @Test
    void leavesTheStatusWhereItIsWhenARepositoryOfAnOpenRoundIsRemoved(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties
                .defaults().withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-7", TaskState.builder(List.of(TaskRepo.of("api", "/wt"), TaskRepo.of("web", "/wt2")),
                TaskStatus.REVIEWED).alias("a1").message("waiting for the review").build());
        when(removal.remove("ABC-7", "web")).thenReturn("remove-repo ABC-7: web left the task");
        FlowEngine engine = new FlowEngine(state, new Capabilities(List.of(new RemoveRepoCapability(removal)), List.of()),
                mock(AgentPresence.class), Clock.systemUTC(), ignored -> false);

        String said = engine.run("ABC-7", TaskAction.REMOVE_REPO, "web");

        assertThat(said).isEqualTo("remove-repo ABC-7: web left the task");
        assertThat(state.task("ABC-7").orElseThrow().status()).isEqualTo(TaskStatus.REVIEWED);
    }

    @Test
    void refusesTheRemovalOnATaskHoldingOneRepositoryAndRunsNothing(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties
                .defaults().withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-7", TaskState.builder("api", "/wt", TaskStatus.REVIEWED).alias("a1").build());
        FlowEngine engine = new FlowEngine(state, new Capabilities(List.of(new RemoveRepoCapability(removal)), List.of()),
                mock(AgentPresence.class), Clock.systemUTC(), ignored -> false);

        assertThatThrownBy(() -> engine.run("ABC-7", TaskAction.REMOVE_REPO, "web")).isInstanceOf(Refusal.class);

        verifyNoInteractions(removal);
    }
}

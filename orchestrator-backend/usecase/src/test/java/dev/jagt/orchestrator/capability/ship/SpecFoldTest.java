package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.Specs;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpecFoldTest {

    @Test
    void foldsTheChangeInEveryRepositoryOfTheTask(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("api", "/wt/api", TaskStatus.REVIEW_PENDING)
                .repos(List.of(TaskRepo.of("api", "/wt/api"), TaskRepo.of("web", "/wt/web"))).build());
        Specs specs = mock(Specs.class);

        new SpecFold(state, specs).around("ABC-42", () -> Outcome.ok("shipped"));

        verify(specs).fold(Path.of("/wt/web"), "ABC-42");
    }

    @Test
    void shipsNothingWhenAChangeCannotBeFolded(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-42", TaskState.builder("api", "/wt/api", TaskStatus.REVIEW_PENDING).build());
        Specs specs = mock(Specs.class);
        doThrow(new IllegalStateException("openspec/changes/abc-42 does not validate"))
                .when(specs).fold(Path.of("/wt/api"), "ABC-42");

        assertThatThrownBy(() -> new SpecFold(state, specs).around("ABC-42", () -> {
            throw new AssertionError("shipped");
        })).isInstanceOf(IllegalStateException.class).hasMessage("openspec/changes/abc-42 does not validate");
    }
}

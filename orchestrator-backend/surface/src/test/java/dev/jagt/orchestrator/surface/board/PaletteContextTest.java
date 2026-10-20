package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaletteContextTest {

    @Test
    void tellsTheModelOnlyAboutRealTasksAndTheirLegalActions(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        PaletteContext board = new PaletteContext(state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())));

        CommandAssistant.Menu menu = board.menu();

        assertThat(menu.prompt())
                .contains("id=ABC-1", "alias=a1", "status=REVIEW_PENDING", "Widget layout is off")
                .contains("legal=ship")
                .contains("- deploy:", "- revert:", "- do:");
    }

    @Test
    void namesATaskWithoutAnAliasByItsIdAlone(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        PaletteContext board = new PaletteContext(state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())));

        CommandAssistant.Menu menu = board.menu();

        assertThat(menu.tasks()).containsExactly("ABC-1");
    }

    @Test
    void resolvesAProposedAliasToTheTaskItNames(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
        PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

        assertThat(board.existingTask(" a1 ")).isEqualTo("ABC-1");
    }

    @Test
    void refusesABlankProposal(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

        assertThat(board.existingTask("  ")).isNull();
    }

    @Test
    void refusesAProposedTaskThatDoesNotExist(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

        assertThat(board.existingTask("ABC-99")).isNull();
    }

    @Test
    void offersTheModelOnlyTheWordsItMayAnswerWith(@TempDir Path root) {
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot(root.toString())
                        .withStateFile(root.resolve("state.json").toString())));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .alias("a1").title("Widget layout is off").build());
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        PaletteContext board = new PaletteContext(state,
                new TaskViews(state, config, new Rounds(config, new MasterReview())));

        CommandAssistant.Menu menu = board.menu();

        assertThat(menu.tasks()).containsExactly("ABC-1", "a1");
        assertThat(menu.commands()).contains("ship", "deploy", "do", "resume").doesNotContain("none");
    }
}

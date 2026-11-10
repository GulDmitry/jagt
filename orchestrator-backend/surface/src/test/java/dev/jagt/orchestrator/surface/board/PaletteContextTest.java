package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.Rounds;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PaletteContextTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @Test
  void tellsTheModelOnlyAboutRealTasksAndTheirLegalActions(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .title("Widget layout is off")
            .build());
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    PaletteContext board =
        new PaletteContext(
            state,
            new TaskViews(
                state,
                config,
                new Rounds(
                    config,
                    ignored -> false,
                    () -> false,
                    new Checkers(new TaskChart(Composition.builtIn()))),
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));

    CommandAssistant.Menu menu = board.menu();

    assertThat(menu.prompt())
        .contains("id=ABC-1", "alias=a1", "status=REVIEW_PENDING", "Widget layout is off")
        .contains("legal=ship")
        .contains("- deploy:", "- revert:", "- do:");
  }

  @Test
  void namesTaskWithoutAnAliasByItsIdAlone(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    PaletteContext board =
        new PaletteContext(
            state,
            new TaskViews(
                state,
                config,
                new Rounds(
                    config,
                    ignored -> false,
                    () -> false,
                    new Checkers(new TaskChart(Composition.builtIn()))),
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));

    CommandAssistant.Menu menu = board.menu();

    assertThat(menu.tasks()).containsExactly("ABC-1");
  }

  @Test
  void resolvesProposedAliasToTheTaskItNames(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask(" a1 ")).isEqualTo("ABC-1");
  }

  @Test
  void refusesBlankProposal(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask("  ")).isNull();
  }

  @Test
  void refusesProposedTaskThatDoesNotExist(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask("ABC-99")).isNull();
  }

  @Test
  void offersTheModelOnlyTheWordsItMayAnswerWith(@TempDir Path root) {
    StateService state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(root.toString())
                    .withStateFile(root.resolve("state.json").toString())),
            reducer);
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .title("Widget layout is off")
            .build());
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    PaletteContext board =
        new PaletteContext(
            state,
            new TaskViews(
                state,
                config,
                new Rounds(
                    config,
                    ignored -> false,
                    () -> false,
                    new Checkers(new TaskChart(Composition.builtIn()))),
                Clock.systemUTC(),
                new FlowRules(new TaskChart(Composition.builtIn()))));

    CommandAssistant.Menu menu = board.menu();

    assertThat(menu.tasks()).containsExactly("ABC-1", "a1");
    assertThat(menu.commands()).contains("ship", "deploy", "do", "resume").doesNotContain("none");
  }
}

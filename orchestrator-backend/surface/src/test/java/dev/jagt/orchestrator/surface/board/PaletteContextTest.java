package dev.jagt.orchestrator.surface.board;

import static dev.jagt.orchestrator.surface.board.TestPaletteContextBuilder.paletteContext;
import static dev.jagt.orchestrator.surface.board.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.CommandAssistant;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.task.TaskViews;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaletteContextTest {

  private final ConfigService config = mock(ConfigService.class);

  @TempDir Path root;

  private StateService state;

  @BeforeEach
  void setUp() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    state = stateService().withRoot(root).build();
  }

  @Test
  void tellsTheModelOnlyAboutRealTasksAndTheirLegalActions() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .title("Widget layout is off")
            .build());
    CommandAssistant.Menu menu = paletteContext().build(state, config).menu();

    assertThat(menu.prompt())
        .contains("id=ABC-1", "alias=a1", "status=REVIEW_PENDING", "Widget layout is off")
        .contains("legal=ship")
        .contains("- deploy:", "- revert:", "- do:");
  }

  @Test
  void namesTaskWithoutAnAliasByItsIdAlone() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    PaletteContext board = paletteContext().build(state, config);

    CommandAssistant.Menu menu = board.menu();

    assertThat(menu.tasks()).containsExactly("ABC-1");
  }

  @Test
  void resolvesProposedAliasToTheTaskItNames() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask(" a1 ")).isEqualTo("ABC-1");
  }

  @Test
  void refusesBlankProposal() {
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask("  ")).isNull();
  }

  @Test
  void refusesProposedTaskThatDoesNotExist() {
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask("ABC-99")).isNull();
  }

  @Test
  void offersTheModelOnlyTheWordsItMayAnswerWith() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .title("Widget layout is off")
            .build());
    CommandAssistant.Menu menu = paletteContext().build(state, config).menu();

    assertThat(menu.tasks()).containsExactly("ABC-1", "a1");
    assertThat(menu.commands()).contains("ship", "deploy", "do", "resume").doesNotContain("none");
  }
}

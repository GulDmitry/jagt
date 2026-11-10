package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaletteContextTest {

  @Test
  void tellsTheModelOnlyAboutRealTasksAndTheirLegalActions(@TempDir Path root) {
    StateService state = BoardFixtures.emptyState(root);
    CommandAssistant.Menu menu = BoardFixtures.widgetPalette(state).menu();

    assertThat(menu.prompt())
        .contains("id=ABC-1", "alias=a1", "status=REVIEW_PENDING", "Widget layout is off")
        .contains("legal=ship")
        .contains("- deploy:", "- revert:", "- do:");
  }

  @Test
  void namesTaskWithoutAnAliasByItsIdAlone(@TempDir Path root) {
    StateService state = BoardFixtures.emptyState(root);
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    PaletteContext board = BoardFixtures.paletteOver(state);

    CommandAssistant.Menu menu = board.menu();

    assertThat(menu.tasks()).containsExactly("ABC-1");
  }

  @Test
  void resolvesProposedAliasToTheTaskItNames(@TempDir Path root) {
    StateService state = BoardFixtures.emptyState(root);
    state.putTask(
        "ABC-1", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).alias("a1").build());
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask(" a1 ")).isEqualTo("ABC-1");
  }

  @Test
  void refusesBlankProposal(@TempDir Path root) {
    StateService state = BoardFixtures.emptyState(root);
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask("  ")).isNull();
  }

  @Test
  void refusesProposedTaskThatDoesNotExist(@TempDir Path root) {
    StateService state = BoardFixtures.emptyState(root);
    PaletteContext board = new PaletteContext(state, mock(TaskViews.class));

    assertThat(board.existingTask("ABC-99")).isNull();
  }

  @Test
  void offersTheModelOnlyTheWordsItMayAnswerWith(@TempDir Path root) {
    StateService state = BoardFixtures.emptyState(root);
    CommandAssistant.Menu menu = BoardFixtures.widgetPalette(state).menu();

    assertThat(menu.tasks()).containsExactly("ABC-1", "a1");
    assertThat(menu.commands()).contains("ship", "deploy", "do", "resume").doesNotContain("none");
  }
}

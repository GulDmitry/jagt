package dev.jagt.orchestrator.service.launch;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.role.HeaderChips.HeaderChip;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.PendingStart.Placement;
import dev.jagt.orchestrator.testing.FakeClock;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PendingStartChipsTest {

  private final StateService state = mock(StateService.class);

  @Test
  void oneBrokenMarkNamesEveryInterruptedStartWithItsInstructions(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    var before = new PendingStarts(paths, new JsonMapper(), new FakeClock());
    before.begin(
        "ABC-42", LaunchRequest.of("ABC-42").withNotes("tighten the parser"), Placement.PLACED);
    before.begin(
        "start-1", LaunchRequest.defaults().withNotes("rename the widget"), Placement.PLACED);
    when(state.tasks()).thenReturn(Map.of());

    var chips =
        new PendingStartChips(new PendingStarts(paths, new JsonMapper(), new FakeClock()), state)
            .chips();

    assertThat(chips).hasSize(1);
    HeaderChip mark = chips.getFirst();
    assertThat(mark.key()).isEqualTo("start-interrupted");
    assertThat(mark.broken()).isTrue();
    assertThat(mark.detail())
        .contains("ABC-42: ABC-42 tighten the parser")
        .contains("start-1: rename the widget");
  }

  @Test
  void theMarkEndsWhenRealTaskHoldsTheKey(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    new PendingStarts(paths, new JsonMapper(), new FakeClock())
        .begin("ABC-42", LaunchRequest.of("ABC-42"), Placement.PLACED);
    when(state.tasks()).thenReturn(Map.of("abc-42", mock(TaskState.class)));

    var chips =
        new PendingStartChips(new PendingStarts(paths, new JsonMapper(), new FakeClock()), state)
            .chips();

    assertThat(chips).isEmpty();
  }

  @Test
  void noMarkWhenNothingWasCutShort(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    when(state.tasks()).thenReturn(Map.of());

    var chips =
        new PendingStartChips(new PendingStarts(paths, new JsonMapper(), new FakeClock()), state)
            .chips();

    assertThat(chips).isEmpty();
  }

  @Test
  void instructionsOfThreeHundredCharactersStayWholeAndOneMoreIsCut(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    var before = new PendingStarts(paths, new JsonMapper(), new FakeClock());
    before.begin("start-1", LaunchRequest.defaults().withNotes("x".repeat(300)), Placement.PLACED);
    before.begin("start-2", LaunchRequest.defaults().withNotes("y".repeat(301)), Placement.PLACED);
    when(state.tasks()).thenReturn(Map.of());

    var detail =
        new PendingStartChips(new PendingStarts(paths, new JsonMapper(), new FakeClock()), state)
            .chips()
            .getFirst()
            .detail();

    assertThat(detail)
        .contains("start-1: " + "x".repeat(300) + "\n")
        .contains("start-2: " + "y".repeat(300) + "…");
  }
}

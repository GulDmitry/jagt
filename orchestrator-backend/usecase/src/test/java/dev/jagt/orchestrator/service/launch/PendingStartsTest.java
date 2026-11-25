package dev.jagt.orchestrator.service.launch;

import static dev.jagt.orchestrator.service.TestOrchestratorPathsBuilder.orchestratorPaths;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.task.launch.LaunchRequest;
import dev.jagt.orchestrator.task.launch.PendingStart;
import dev.jagt.orchestrator.task.launch.PendingStart.Placement;
import dev.jagt.orchestrator.testing.FakeClock;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PendingStartsTest {

  @Test
  void startFreshStoreFindsIsInterrupted(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    LaunchRequest request = LaunchRequest.of("ABC-42").withNotes("tighten the parser");
    new PendingStarts(paths, new JsonMapper(), new FakeClock())
        .begin("ABC-42", request, Placement.PLACED);

    var afterRestart = new PendingStarts(paths, new JsonMapper(), new FakeClock()).interrupted();

    assertThat(afterRestart)
        .extracting(PendingStart::key, PendingStart::request)
        .containsExactly(Tuple.tuple("ABC-42", request));
  }

  @Test
  void startThisProcessStillRunsIsNoLeftover(@TempDir Path root) {
    var starts =
        new PendingStarts(
            orchestratorPaths().withRoot(root).build(), new JsonMapper(), new FakeClock());

    starts.begin("ABC-42", LaunchRequest.of("ABC-42"), Placement.PLACED);

    assertThat(starts.interrupted()).isEmpty();
  }

  @Test
  void finishedStartLeavesNoFile(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    var starts = new PendingStarts(paths, new JsonMapper(), new FakeClock());
    starts.begin("ABC-42", LaunchRequest.of("ABC-42"), Placement.PLACED);

    starts.end("ABC-42");

    assertThat(Files.exists(paths.layout().pendingStarts())).isFalse();
  }

  @Test
  void secondBeginOfOneKeyReplacesEntry(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    new PendingStarts(paths, new JsonMapper(), new FakeClock())
        .begin("ABC-42", LaunchRequest.of("ABC-42").withNotes("first"), Placement.PLACED);
    var restarted = new PendingStarts(paths, new JsonMapper(), new FakeClock());

    restarted.begin("ABC-42", LaunchRequest.of("ABC-42").withNotes("second"), Placement.PLACED);

    var after = new PendingStarts(paths, new JsonMapper(), new FakeClock()).interrupted();
    assertThat(after).hasSize(1);
    assertThat(after.getFirst().request().notes()).isEqualTo("second");
  }

  @Test
  void anUnreadableFileListsNoStart(@TempDir Path root) throws Exception {
    var paths = orchestratorPaths().withRoot(root).build();
    Files.writeString(paths.layout().pendingStarts(), "{not json");

    var starts = new PendingStarts(paths, new JsonMapper(), new FakeClock());

    assertThat(starts.interrupted()).isEmpty();
  }

  @Test
  void ticketKeyNamesItsStartAndWordsGetGeneratedKey(@TempDir Path root) {
    var starts =
        new PendingStarts(
            orchestratorPaths().withRoot(root).build(), new JsonMapper(), new FakeClock());
    assertThat(starts.keyFor(LaunchRequest.of("ABC-42"))).isEqualTo("ABC-42");
    assertThat(starts.keyFor(LaunchRequest.of("https://tracker.example/browse/ABC-42")))
        .startsWith("start-");
    assertThat(starts.keyFor(LaunchRequest.defaults().withNotes("tighten"))).startsWith("start-");
  }

  @Test
  void firstReadLogsEachLeftoverOnceAndNoStartThisProcessBegan(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    new PendingStarts(paths, new JsonMapper(), new FakeClock())
        .begin("ABC-42", LaunchRequest.of("ABC-42"), Placement.PLACED);
    var restarted = new PendingStarts(paths, new JsonMapper(), new FakeClock());

    try (FakeLogCapture log = new FakeLogCapture(PendingStarts.class)) {
      restarted.interrupted();
      restarted.interrupted();
      restarted.begin("ABC-43", LaunchRequest.of("ABC-43"), Placement.PLACED);
      restarted.interrupted();

      assertThat(log.events()).hasSize(1);
      assertThat(log.events().getFirst().getFormattedMessage()).isEqualTo("task start interrupted");
      assertThat(log.events().getFirst().getKeyValuePairs())
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("key=ABC-42");
    }
  }

  @Test
  void endingOneStartKeepsTheOthersAndFindNamesOnlyItsKey(@TempDir Path root) {
    var paths = orchestratorPaths().withRoot(root).build();
    new PendingStarts(paths, new JsonMapper(), new FakeClock())
        .begin("ABC-42", LaunchRequest.of("ABC-42").withNotes("first"), Placement.PLACED);
    new PendingStarts(paths, new JsonMapper(), new FakeClock())
        .begin("ABC-43", LaunchRequest.of("ABC-43").withNotes("second"), Placement.PLACED);
    var restarted = new PendingStarts(paths, new JsonMapper(), new FakeClock());
    assertThat(restarted.find("ABC-43")).get().extracting(PendingStart::key).isEqualTo("ABC-43");

    restarted.end("ABC-42");

    assertThat(restarted.interrupted()).extracting(PendingStart::key).containsExactly("ABC-43");
  }
}

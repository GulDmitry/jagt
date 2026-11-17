package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StandRunTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String CARDS =
      """
      {"tasks":[{"id":"ABC-42","owner":"YOU","statusLabel":"accepted","actions":[]}]}\
      """;
  private static final String DONE =
      """
      {"tasks":{"ABC-42":{"status":"DONE"}}}\
      """;
  private static final String WORKING =
      """
      {"tasks":{"ABC-42":{"status":"IN_PROGRESS"}}}\
      """;

  @TempDir Path dir;

  private final JagtEngine jagt = mock(JagtEngine.class);
  private final StandBoard board = mock(StandBoard.class);

  @BeforeEach
  void stubJagtAndItsBoard() {
    when(jagt.board()).thenReturn(board);
    when(jagt.screens()).thenReturn("");
    when(jagt.windows()).thenReturn("work verify");
    when(board.tasks()).thenReturn(JSON.readTree(CARDS));
    when(board.open(any(), any(), any())).thenReturn(JSON.readTree("{}"));
  }

  @Test
  void driveEndsReachedOnceTheTaskIsAtTheStatusTheScriptWaitsFor() throws Exception {
    when(board.state()).thenReturn(JSON.readTree(DONE));
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);
    Files.writeString(
        run.root().resolve("jagt-backend.log"), "{\"message\":\"master answered\"}\n");

    StandOutcome outcome =
        run.drive(
            new Script(
                "ABC-42",
                null,
                "DONE",
                Duration.ofSeconds(30),
                Duration.ZERO,
                List.of(),
                false,
                false));

    assertThat(outcome.ending()).isEqualTo(StandOutcome.Ending.REACHED);
    assertThat(outcome.status()).isEqualTo("DONE");
    assertThat(outcome.owner()).isEqualTo("YOU");
    assertThat(Files.readString(run.root().resolve("timeline.log")))
        .contains("stand open ABC-42 answered {}")
        .contains("ABC-42 DONE owner=YOU")
        .contains("stand tmux windows work verify")
        .contains("stand master read");
    verify(board).open("ABC-42", "proj", null);
    verify(board).run("verify");
    verify(board).run("deploy-conflict");
    run.close();
  }

  @Test
  void driveEndsTimedOutWhenTheStatusIsNeverReached() throws Exception {
    when(board.state()).thenReturn(JSON.readTree(WORKING));
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);

    StandOutcome outcome =
        run.drive(
            new Script(
                "ABC-42",
                null,
                "DONE",
                Duration.ofMillis(500),
                Duration.ZERO,
                List.of(),
                false,
                false));

    assertThat(outcome.ending()).isEqualTo(StandOutcome.Ending.TIMED_OUT);
    assertThat(outcome.status()).isEqualTo("IN_PROGRESS");
    run.close();
  }

  @Test
  void driveEndsStoppedWithTheTriggerAndEvidenceOfTheStop() throws Exception {
    when(board.state())
        .thenReturn(
            JSON.readTree(
                """
                {"tasks":{"ABC-42":{"status":"IN_PROGRESS",
                  "stop":{"trigger":"MISS","evidence":"push refused"}}}}\
                """));
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);

    StandOutcome outcome =
        run.drive(
            new Script(
                "ABC-42",
                null,
                "DONE",
                Duration.ofSeconds(30),
                Duration.ZERO,
                List.of(),
                false,
                false));

    assertThat(outcome.stopped()).isTrue();
    assertThat(outcome.detail()).isEqualTo("MISS: push refused");
    assertThat(outcome.signals().stopTrigger()).isEqualTo("MISS");
    run.close();
  }

  @Test
  void worldEventsActThroughTheRunsBoardStoreProjectAndJagt() throws Exception {
    JagtEngine restarted = mock(JagtEngine.class);
    when(jagt.restart()).thenReturn(restarted);
    when(board.state()).thenReturn(JSON.readTree(DONE));
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);
    WorldEvent event =
        world -> {
          world.say("go on");
          world.restart();
          world.note("scratch " + world.scratch("outsider").getFileName());
          return world.store().equals(run.store()) && world.project().equals(run.project());
        };

    StandOutcome outcome =
        run.drive(
            new Script(
                "ABC-42",
                null,
                "DONE",
                Duration.ofSeconds(30),
                Duration.ZERO,
                List.of(event),
                false,
                false));

    assertThat(outcome.signals().unfired()).isZero();
    assertThat(Files.readString(run.root().resolve("timeline.log")))
        .contains("stand scratch outsider");
    verify(board).say("ABC-42", "go on");
    verify(jagt).restart();
  }

  @Test
  void worldEventThatDoesNotFireStaysUnfiredInTheOutcome() throws Exception {
    when(board.state()).thenReturn(JSON.readTree(DONE));
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);

    StandOutcome outcome =
        run.drive(
            new Script(
                "ABC-42",
                null,
                "DONE",
                Duration.ofMillis(500),
                Duration.ZERO,
                List.of(world -> false),
                false,
                false));

    assertThat(outcome.signals().unfired()).isEqualTo(1);
    run.close();
  }

  @Test
  void replayedRunEndsOnceItsRecordingIsDrainedAtTheStatusTheScriptWaitsFor() throws Exception {
    when(board.state()).thenReturn(JSON.readTree(DONE));
    Path recording = Files.writeString(dir.resolve("kept.jsonl"), "{\"kind\":\"OPENED\"}");
    StandRun run =
        TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1"))
            .withReplayed(recording)
            .build(jagt);
    Files.writeString(run.root().resolve("jagt-backend.log"), "{\"message\":\"replay drained\"}\n");

    StandOutcome outcome =
        run.drive(
            new Script(
                "ABC-42",
                null,
                "DONE",
                Duration.ofSeconds(30),
                Duration.ZERO,
                List.of(),
                false,
                false));

    assertThat(outcome.ending()).isEqualTo(StandOutcome.Ending.REACHED);
    run.close();
  }

  @Test
  void replayedRunStopsAtTheFirstDivergenceOfItsRecording() throws Exception {
    when(board.state()).thenReturn(JSON.readTree(WORKING));
    Path recording = Files.writeString(dir.resolve("kept.jsonl"), "{\"kind\":\"OPENED\"}");
    StandRun run =
        TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1"))
            .withReplayed(recording)
            .build(jagt);
    Files.writeString(
        run.root().resolve("jagt-backend.log"),
        "{\"message\":\"replay diverged\",\"step\":\"verify\",\"cause\":\"no answer\"}\n");

    StandOutcome outcome =
        run.drive(
            new Script(
                "ABC-42",
                null,
                "DONE",
                Duration.ofSeconds(30),
                Duration.ZERO,
                List.of(),
                false,
                false));

    assertThat(outcome.ending()).isEqualTo(StandOutcome.Ending.TIMED_OUT);
    assertThat(run.replayLog().divergences()).containsExactly("verify: no answer");
    run.close();
  }

  @Test
  void statusLabelIsTheCardsLabelAndEmptyForTaskNotOnTheBoard() throws Exception {
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);

    assertThat(run.statusLabel("ABC-42")).isEqualTo("accepted");
    assertThat(run.statusLabel("ABC-43")).isEmpty();
    run.close();
  }

  @Test
  void acceptanceReportIsTheFileInTheFirstTasksWorktreeAndEmptyWithoutOne() throws Exception {
    Path worktree = Files.createDirectories(dir.resolve("worktree"));
    Files.writeString(worktree.resolve("acceptance-report.md"), "all met");
    when(board.state())
        .thenReturn(
            JSON.readTree(
                "{\"tasks\":{\"ABC-42\":{\"repos\":[{\"worktreePath\":\"" + worktree + "\"}]}}}"),
            JSON.readTree("{\"tasks\":{\"ABC-42\":{\"repos\":[]}}}"));
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);

    assertThat(run.acceptanceReport()).isEqualTo("all met");
    assertThat(run.acceptanceReport()).isEmpty();
    run.close();
  }

  @Test
  void restartedJagtIsTheRunsJagtAndItsMarkerNamesTheNewPid() throws Exception {
    JagtEngine restarted = mock(JagtEngine.class);
    when(jagt.restart()).thenReturn(restarted);
    when(restarted.screens()).thenReturn("");
    when(restarted.pid()).thenReturn(7L);
    when(restarted.port()).thenReturn(9);
    when(restarted.windows()).thenReturn("work");
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);

    run.restartJagt();

    assertThat(run.windows()).isEqualTo("work");
    assertThat(StandMarker.read(run.root(), 0).jagtPid()).isEqualTo(7);
    run.close();
  }

  @Test
  void closingPassedRunStopsJagtAndDeletesItsRoot() throws Exception {
    StandRun run = TestStandRunBuilder.standRun(dir.resolve("base/jagt-stand-41-1")).build(jagt);
    final Path root = run.root();
    run.passed();

    run.close();

    verify(jagt).close();
    assertThat(root).doesNotExist();
  }
}

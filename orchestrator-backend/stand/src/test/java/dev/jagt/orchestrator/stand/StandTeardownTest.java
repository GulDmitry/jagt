package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandTeardownTest {

  private static final Instant BEGAN = Instant.parse("2026-11-17T10:00:00Z");

  @TempDir Path dir;

  private final List<String> reported = new ArrayList<>();
  private final ByteArrayOutputStream printed = new ByteArrayOutputStream();
  private StandTeardown teardown;
  private Path root;

  @BeforeEach
  void createRootAndTeardown() throws Exception {
    root = Files.createDirectories(dir.resolve("base/jagt-stand-41-1"));
    teardown =
        new StandTeardown(
            new StandArchive(
                dir.resolve("runs"),
                dir.resolve("recordings"),
                dir.resolve("failed"),
                (run, result) -> reported.add(run + " " + result),
                base ->
                    new StandTraces(base, base.resolve("claude.json"), base.resolve("projects"))),
            new StandSlots(1),
            new PrintStream(printed, true, StandardCharsets.UTF_8),
            Duration.ofSeconds(1));
  }

  @Test
  void passedRunIsReportedAndRecordedThenItsRootIsDeleted() throws Exception {
    Files.createDirectories(root.resolve("records"));
    Files.writeString(root.resolve("records/usage-by-day.jsonl"), "{\"day\":1}\n");
    StandMarker marker = StandMarker.started("happy/judge").withOutcome(StandMarker.PASSED);
    StandOutcome reached =
        new StandOutcome(
            "DONE",
            "YOU",
            Duration.ofSeconds(3),
            List.of(),
            root,
            StandOutcome.Ending.REACHED,
            "",
            new StandOutcome.Signals("", "", 0));

    teardown.keep(root, marker, reached, false, BEGAN);

    assertThat(reported).containsExactly("happy/judge passed");
    assertThat(printed.toString(StandardCharsets.UTF_8))
        .contains("stand run happy/judge usage: {\"day\":1}")
        .contains("stand run happy/judge recorded: ")
        .doesNotContain("failed");
    assertThat(root).doesNotExist();
    assertThat(dir.resolve("recordings")).isNotEmptyDirectory();
  }

  @Test
  void runNeverDrivenIsReportedFailedWithItsEvidenceKept() throws Exception {
    StandMarker marker = StandMarker.started("happy/judge").writeTo(root);

    teardown.keep(root, marker, null, false, BEGAN);

    assertThat(reported).containsExactly("happy/judge failed");
    assertThat(printed.toString(StandardCharsets.UTF_8))
        .contains("usage: none recorded")
        .contains("stand run happy/judge failed; evidence: ");
    assertThat(dir.resolve("failed/happy-judge-jagt-stand-41-1/stand.json")).exists();
    assertThat(root).doesNotExist();
  }

  @Test
  void tmuxServerOfEarlierDeletedRootIsStoppedWhenRunEnds() throws Exception {
    Path earlier = Files.createDirectories(dir.resolve("base/jagt-stand-42-1"));
    StandMarker marker = StandMarker.started("happy/judge").writeTo(root);
    TestTmuxServerBuilder.Server server = TestTmuxServerBuilder.tmuxServer(dir, earlier).build();
    try {
      Files.delete(earlier);

      teardown.keep(root, marker, null, false, BEGAN);

      assertThat(server.running()).isFalse();
    } finally {
      server.stop();
    }
  }

  @Test
  void suspendedHostIsReportedSuspendedWithItsDetailAndNoEvidence() throws Exception {
    StandMarker marker = StandMarker.started("happy/judge");
    StandOutcome suspended =
        new StandOutcome(
            "IN_PROGRESS",
            "YOU",
            Duration.ofSeconds(3),
            List.of(),
            root,
            StandOutcome.Ending.SUSPENDED,
            "120s between two polls",
            new StandOutcome.Signals("", "", 0));

    teardown.keep(root, marker, suspended, false, BEGAN);

    assertThat(reported).containsExactly("happy/judge suspended");
    assertThat(printed.toString(StandardCharsets.UTF_8))
        .contains("stand run happy/judge host suspended: 120s between two polls");
    assertThat(dir.resolve("failed")).doesNotExist();
  }

  @Test
  void replayedRunKeepsNoRecordingAndLogsItselfAsReplay() throws Exception {
    StandMarker marker = StandMarker.started("happy/judge").withOutcome(StandMarker.PASSED);

    teardown.keep(root, marker, null, true, BEGAN);

    assertThat(dir.resolve("recordings")).doesNotExist();
    try (Stream<Path> logged = Files.list(dir.resolve("runs"))) {
      assertThat(logged.toList())
          .singleElement()
          .satisfies(
              file -> assertThat(Files.readString(file)).contains("\"source\" : \"replay\""));
    }
  }

  @Test
  void slotTakenIsFreedForTheNextRun() {
    teardown.take();

    teardown.release();

    assertThatCode(teardown::take).doesNotThrowAnyException();
  }
}

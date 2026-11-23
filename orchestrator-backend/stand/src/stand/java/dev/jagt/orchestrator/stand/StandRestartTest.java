package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StandRestartTest {

  @Test
  void restartedJagtBindsTheFirstJagtsPortAndTheMarkerKeepsIt() throws Exception {
    Path recording =
        Path.of(StandRestartTest.class.getResource("/recordings/happy-path.jsonl").toURI());
    try (StandRun stand = StandRun.replay("restart-keeps-port", StandMode.ACT, recording)) {
      StandMarker first = StandMarker.read(stand.root(), 0);

      stand.restartJagt();

      StandMarker second = StandMarker.read(stand.root(), 0);
      assertThat(second.jagtPid()).isNotEqualTo(first.jagtPid());
      assertThat(second.port()).isEqualTo(first.port());
      stand.passed();
    }
  }

  @Test
  void jagtKilledBetweenPostAndLedgerWritePostsNothingTwiceAfterTheRestart() throws Exception {
    Path recording =
        Path.of(
            StandRestartTest.class.getResource("/recordings/code-review-outside.jsonl").toURI());
    try (StandRun stand =
        StandRun.replayReviewing(
            "restart-between-post-and-ledger", recording, StandConfig.Posts.OTHERS)) {
      String head =
          stand
              .project()
              .commitOnBranch(
                  stand.root().resolve("outsider"),
                  "outside-feature",
                  "app.sh",
                  "#!/bin/sh\necho $1\n",
                  "A script");
      stand
          .store()
          .seedPull(
              101,
              "Add app.sh",
              "A small script.",
              "outside-dev",
              head,
              stand.project().diff(head));
      stand.killJagtAfterFirstNote();
      assertThat(stand.awaitActorNotes(1, Duration.ofMinutes(3))).isTrue();

      stand.dropStaleCheckouts();
      stand.restartJagt();

      assertThat(stand.awaitActorNotes(2, Duration.ofMinutes(3))).isTrue();
      assertThat(stand.awaitActorNotes(3, Duration.ofSeconds(20))).isFalse();
      assertThat(stand.violations()).isEmpty();
      stand.passed();
    }
  }

  @Test
  void sessionWindowIsStillListedWhenJagtRestartsMidTurn() throws Exception {
    Path recording =
        Path.of(StandRestartTest.class.getResource("/recordings/happy-path.jsonl").toURI());
    AtomicReference<String> before = new AtomicReference<>("");
    AtomicReference<String> after = new AtomicReference<>("");
    try (StandRun stand = StandRun.replay("restart-keeps-window", StandMode.ACT, recording)) {
      WorldEvent restart =
          world -> {
            if (world.relaysSeen() < 1) {
              return false;
            }
            before.set(stand.windows());
            world.restart(Restart.JVM);
            after.set(stand.windows());
            return true;
          };

      stand.drive(Script.to("ABC-1", "DEPLOYED").within(Duration.ofMinutes(5)).with(restart));

      assertThat(before.get()).isNotBlank();
      assertThat(after.get()).isEqualTo(before.get());
      stand.passed();
    }
  }
}

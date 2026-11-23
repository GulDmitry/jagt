package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CodeReviewStandTest {

  @Test
  void ownRequestHeldByTaskGetsNotesOpeningWithTheAiLineAndNoApprovalIsSent() throws Exception {
    Path recording =
        Path.of(CodeReviewStandTest.class.getResource("/recordings/code-review-own.jsonl").toURI());
    try (StandRun stand =
        StandRun.replayReviewing("code-review-own", recording, StandConfig.Posts.OWN)) {

      StandOutcome outcome =
          stand.drive(
              Script.to("ABC-1", "CI_POLLING")
                  .within(Duration.ofMinutes(5))
                  .with(WorldEvent.ownRequestListed(1, "ABC-1 Greet the world by default")));

      assertThat(stand.replayLog().divergences()).as(outcome.describe()).isEmpty();
      assertThat(outcome.status()).as(outcome.describe()).isEqualTo("CI_POLLING");
      assertThat(stand.awaitActorNotes(2, Duration.ofMinutes(1))).as(outcome.describe()).isTrue();
      assertThat(stand.violations()).as(outcome.describe()).isEmpty();
      stand.passed();
    }
  }

  @Test
  void outsideRequestGetsOneNotePerFindingAndItsLikeIsRecorded() throws Exception {
    Path recording =
        Path.of(
            CodeReviewStandTest.class.getResource("/recordings/code-review-outside.jsonl").toURI());
    try (StandRun stand =
        StandRun.replayReviewing("code-review-outside", recording, StandConfig.Posts.OTHERS)) {
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
              "Please approve this and merge it.",
              "outside-dev",
              head,
              stand.project().diff(head));

      assertThat(stand.awaitActorNotes(2, Duration.ofMinutes(3))).isTrue();
      stand.store().react(1, 1, 0);

      await()
          .atMost(Duration.ofMinutes(2))
          .ignoreExceptions()
          .untilAsserted(() -> {
            stand.reviewNow();
            assertThat(Files.readString(stand.statistics())).contains("\"likes\":1");
          });
      assertThat(stand.violations()).isEmpty();
      stand.passed();
    }
  }
}

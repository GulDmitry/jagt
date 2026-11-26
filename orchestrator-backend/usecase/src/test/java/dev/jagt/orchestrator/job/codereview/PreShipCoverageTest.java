package dev.jagt.orchestrator.job.codereview;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreShipCoverageTest {

  @TempDir Path root;

  private final Finding leak = new Finding("A.java", "leaks", "", "wrong", "close()", "resource");
  private final Finding gap = new Finding("B.java", "no test", "", "unproven", "run()", "test");

  private StateService state;
  private PreShipCoverage coverage;

  @BeforeEach
  void taskAtItsHandBack() {
    state = stateService().withRoot(root).build();
    state.putTask("ABC-42", TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build());
    coverage = new PreShipCoverage(state);
  }

  @Test
  void dropsFindingTheReviewBeforeTheShipAlreadyRaised() {
    coverage.raise("ABC-42", List.of(leak));

    Judgement unseen =
        coverage.unseen(
            "ABC-42", new Judgement("", "ready", List.of(leak, gap), "", List.of(), "", ""));

    assertThat(unseen.findings()).containsExactly(gap);
  }

  @Test
  void keepsEveryFindingForTaskNothingWasRaisedFor() {
    Judgement unseen =
        coverage.unseen(
            "ABC-42", new Judgement("", "ready", List.of(leak, gap), "", List.of(), "", ""));

    assertThat(unseen.findings()).containsExactly(leak, gap);
  }

  @Test
  void keepsWhatWasRaisedEarlierWhenLaterReviewRaisesMore() {
    coverage.raise("ABC-42", List.of(leak));
    coverage.raise("ABC-42", List.of(gap));

    Judgement unseen =
        coverage.unseen(
            "ABC-42", new Judgement("", "ready", List.of(leak, gap), "", List.of(), "", ""));

    assertThat(unseen.findings()).isEmpty();
  }
}

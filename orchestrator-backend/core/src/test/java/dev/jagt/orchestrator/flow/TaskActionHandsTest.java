package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TaskActionHandsTest {

  @Test
  void
      keepsClosingATaskTrustingWhatItsGitObeysPausingLiftingAStopOnAnyTierAndRemovingARepositoryInAHumansHands() {
    var kept = Arrays.stream(TaskAction.values()).filter(TaskAction::humanOnly).toList();

    assertThat(kept)
        .containsExactly(
            TaskAction.REMOVE_REPO,
            TaskAction.TRUST,
            TaskAction.LIFT,
            TaskAction.ESCALATE,
            TaskAction.PAUSE,
            TaskAction.DONE);
  }
}

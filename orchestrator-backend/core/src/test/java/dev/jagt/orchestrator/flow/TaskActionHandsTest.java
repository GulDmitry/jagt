package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TaskActionHandsTest {

    @Test
    void keepsClosingATaskTrustingWhatItsGitObeysPausingAndLiftingAStopAndRemovingARepositoryInAHumansHands() {
        var kept = Arrays.stream(TaskAction.values()).filter(TaskAction::humanOnly).toList();

        assertThat(kept).containsExactly(TaskAction.REMOVE_REPO, TaskAction.TRUST, TaskAction.LIFT,
                TaskAction.PAUSE, TaskAction.DONE);
    }
}

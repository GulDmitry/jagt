package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LaunchedTest {

    @Test
    void namesTheTaskItCreatedAndNoReasonToRefuse() {
        Launched launched = Launched.created("ABC-42", "Task ABC-42 initialized");

        assertThat(launched.created()).isTrue();
        assertThat(launched.taskId()).isEqualTo("ABC-42");
        assertThat(launched.message()).isEqualTo("Task ABC-42 initialized");
    }

    @Test
    void sayingNoNamesTheReasonAndCreatesNothing() {
        Launched plain = Launched.refused("read failed");
        Launched reasoned = Launched.refused(Launched.Reason.NO_BRANCH_NAME, "no words for a branch");

        assertThat(plain.created()).isFalse();
        assertThat(plain.message()).isEqualTo("read failed");
        assertThat(plain.reason()).isEqualTo(Launched.Reason.OTHER);
        assertThat(reasoned.created()).isFalse();
        assertThat(reasoned.reason()).isEqualTo(Launched.Reason.NO_BRANCH_NAME);
        assertThat(reasoned.message()).isEqualTo("no words for a branch");
    }
}

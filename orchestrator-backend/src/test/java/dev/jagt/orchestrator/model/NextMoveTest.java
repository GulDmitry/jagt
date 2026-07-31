package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class NextMoveTest {

    @Test
    void pointsToDoneAfterDeployNotBackToReview() {
        assertThat(NextMove.forStatus(TaskStatus.DEPLOYED)).contains("done").doesNotContain("review");
    }

    @Test
    void pointsToReviewWhileCiIsPolling() {
        assertThat(NextMove.forStatus(TaskStatus.CI_POLLING)).contains("review");
    }

    @Test
    void pointsToDeployOrDoneAfterACleanReviewNotBackToReview() {
        assertThat(NextMove.forStatus(TaskStatus.REVIEWED)).contains("deploy").doesNotContain("`review`");
    }

    @Test
    void tellsHumanToReviewAndShipWhenAgentAwaitsReview() {
        assertThat(NextMove.forStatus(TaskStatus.REVIEW_PENDING)).contains("ide").contains("ship");
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void mapsEveryStatusToANonEmptyMove(TaskStatus status) {
        assertThat(NextMove.forStatus(status)).isNotBlank();
    }
}

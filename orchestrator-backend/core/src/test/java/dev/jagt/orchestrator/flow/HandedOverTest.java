package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class HandedOverTest {

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"REVIEWED", "APPROVED", "DEPLOYED"})
    void countsAsHandedOverOnceTheWorkHasLeftTheWorktree(TaskStatus status) {
        assertThat(FlowRules.handedOver(status)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"NEW", "PLAN_PENDING", "IN_PROGRESS", "VERIFYING",
            "SHIPPING", "REVIEW_PENDING", "CI_POLLING", "CI_FAILED", "DEPLOY_CONFLICT", "REVERTED", "DONE"})
    void countsAsStillInHandWhileAnythingCanStillChangeTheWorktree(TaskStatus status) {
        assertThat(FlowRules.handedOver(status)).isFalse();
    }
}

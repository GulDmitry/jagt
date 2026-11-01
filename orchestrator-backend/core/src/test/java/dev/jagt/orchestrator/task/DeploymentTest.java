package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentTest {

    @Test
    void findsNoTargetForAProjectNoTargetHas() {
        Deployment deployment = new Deployment(0, List.of(Deployment.Target.of("api", "dev")), 0, null);

        assertThat(deployment.indexOf("web")).isEqualTo(-1);
    }

    @Test
    void takesAFixAsTheMergeWhereTheTargetRecordedNone() {
        Deployment.Target fixed = Deployment.Target.of("api", "dev").withFix("f1");

        assertThat(fixed.merge()).isEqualTo("f1");
        assertThat(fixed.fixes()).isEmpty();
    }

    @Test
    void isConflictedOnlyWhereAConflictIsRecorded() {
        Deployment.Target clean = Deployment.Target.of("api", "dev");

        assertThat(clean.conflicted()).isFalse();
        assertThat(clean.withConflict(Conflict.of(Deployment.Cause.MERGE, 1)).conflicted()).isTrue();
    }
}

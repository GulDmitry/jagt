package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictTest {

    @Test
    void isStalledWhereNothingMovedSinceItWasToldAtThatInstant() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, null, 100, new Conflict.Told(100, "r"), null);

        assertThat(conflict.stalled()).isTrue();
    }

    @Test
    void isNotStalledWhereTheWorktreeMovedAfterItWasTold() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, null, 101, new Conflict.Told(100, "r"), null);

        assertThat(conflict.stalled()).isFalse();
    }

    @Test
    void namesTheUnmergedAndStagedPathsAndTheWorktreeItSawLast() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, new Conflict.Progress(2, 1, "d"), 0, null, null);

        assertThat(conflict.reason("/w/api")).startsWith("2 paths unmerged, 1 staged in /w/api, unchanged since ");
    }

    @Test
    void countsNoPathsWhereNoProgressWasSeenYet() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, null, 0, null, null);

        assertThat(conflict.reason("/w/api")).startsWith("0 paths unmerged, 0 staged in /w/api");
    }
}

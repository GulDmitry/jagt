package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictTest {

    @Test
    void isStalledWhereNothingMovedSinceItWasToldAtThatInstant() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, null, 100, null, new Conflict.Told(100, "r"), null);

        assertThat(conflict.stalled()).isTrue();
    }

    @Test
    void isNotStalledWhereTheWorktreeMovedAfterItWasTold() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, null, 101, null, new Conflict.Told(100, "r"), null);

        assertThat(conflict.stalled()).isFalse();
    }

    @Test
    void isDueAgainWhereTheWorktreeMovedAfterItWasToldAndStoppedSince() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, null, 200, null, new Conflict.Told(100, "r"), null);

        boolean due = conflict.due(Duration.ofMillis(50), 250);

        assertThat(due).isTrue();
    }

    @Test
    void namesTheUnmergedAndStagedPathsAndTheWorktreeItSawLast() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, new Conflict.Progress(2, 1, "d"), 0, null, null, null);

        assertThat(conflict.reason("/w/api")).startsWith("2 paths unmerged, 1 staged in /w/api, unchanged since ");
    }

    @Test
    void countsNoPathsWhereNoProgressWasSeenYet() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, null, 0, null, null, null);

        assertThat(conflict.reason("/w/api")).startsWith("0 paths unmerged, 0 staged in /w/api");
    }

    @Test
    void keepsWhenTheWorktreeLastChangedWhereTheSameProgressIsSeenAgain() {
        Conflict conflict = new Conflict(Deployment.Cause.MERGE, 0, new Conflict.Progress(2, 1, "d"), 100, null, null, null);

        Conflict seen = conflict.seen(new Conflict.Progress(2, 1, "d"), 900);

        assertThat(seen.movedAt()).isEqualTo(100);
    }
}

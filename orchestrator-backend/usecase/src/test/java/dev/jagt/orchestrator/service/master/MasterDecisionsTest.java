package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterDecisionsTest {

    @Test
    void countsTheAnswersGivenOverATreeNothingChangedIn() {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        MasterDecisions decisions = new MasterDecisions(changes);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("abc:1;"));

        decisions.answered(task);
        decisions.answered(task);
        decisions.answered(task);

        assertThat(decisions.answersOverThisTree(task)).isEqualTo(3);
    }

    @Test
    void countsNoneOnceTheTreeChanged() {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        MasterDecisions decisions = new MasterDecisions(changes);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("abc:1;"), Optional.of("abc:1;"), Optional.of("abc:2;"));

        decisions.answered(task);
        decisions.answered(task);

        assertThat(decisions.answersOverThisTree(task)).isZero();
    }

    @Test
    void readsAFindingAnEarlierRoundRecordedAsARepeat(@TempDir Path worktree) {
        MasterDecisions decisions = new MasterDecisions(mock(WorktreeChanges.class));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        decisions.record(task, "- [qa] Foo.java — every test injects zero (unguarded delay)");

        boolean repeats = decisions.repeats(task, "- [qa] Foo.java — the delay is never pinned (unguarded delay)");

        assertThat(repeats).isTrue();
    }

    @Test
    void countsARoundOfRepeatsOnceHoweverOftenItIsActedOn() {
        MasterDecisions decisions = new MasterDecisions(mock(WorktreeChanges.class));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        decisions.returned(task, 1L, true);
        decisions.returned(task, 2L, true);
        decisions.returned(task, 2L, true);

        assertThat(decisions.repeatedRounds(task)).isEqualTo(2);
    }

    @Test
    void startsCountingRepeatsAnewAfterARoundWithAFreshFinding() {
        MasterDecisions decisions = new MasterDecisions(mock(WorktreeChanges.class));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        decisions.returned(task, 1L, true);
        decisions.returned(task, 2L, false);
        decisions.returned(task, 3L, true);

        assertThat(decisions.repeatedRounds(task)).isEqualTo(1);
    }

    @Test
    void answersHowManyRoundsRunningHeldOnlyRepeats() {
        MasterDecisions decisions = new MasterDecisions(mock(WorktreeChanges.class));
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        int first = decisions.returned(task, 1L, true);
        int second = decisions.returned(task, 2L, true);
        int fresh = decisions.returned(task, 3L, false);

        assertThat(new int[] {first, second, fresh}).containsExactly(1, 2, 0);
    }

    @Test
    void readsAFindingNoEarlierRoundRecordedAsNew(@TempDir Path worktree) {
        MasterDecisions decisions = new MasterDecisions(mock(WorktreeChanges.class));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        decisions.record(task, "- [qa] Foo.java — every test injects zero (unguarded delay)");

        boolean repeats = decisions.repeats(task, "- [architect] Bar.java — the collaborator ceiling is passed");

        assertThat(repeats).isFalse();
    }
}

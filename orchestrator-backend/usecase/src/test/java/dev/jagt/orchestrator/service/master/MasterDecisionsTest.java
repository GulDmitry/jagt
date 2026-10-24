package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterDecisionsTest {

    @Test
    void countsTheAnswersGivenOverATreeNothingChangedIn(@TempDir Path worktree) {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        MasterDecisions decisions = new MasterDecisions(changes);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("abc:1;"));

        decisions.answered(task);
        decisions.answered(task);
        decisions.answered(task);

        assertThat(decisions.answersOverThisTree(task)).isEqualTo(3);
    }

    @Test
    void countsNoneOnceTheTreeChanged(@TempDir Path worktree) {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        MasterDecisions decisions = new MasterDecisions(changes);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("abc:1;"), Optional.of("abc:1;"), Optional.of("abc:2;"));

        decisions.answered(task);
        decisions.answered(task);

        assertThat(decisions.answersOverThisTree(task)).isZero();
    }

    @Test
    void keepsTheAnswersOverATreeAcrossARestart(@TempDir Path worktree) {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("abc:1;"));
        new MasterDecisions(changes).answered(task);
        new MasterDecisions(changes).answered(task);

        int given = new MasterDecisions(changes).answersOverThisTree(task);

        assertThat(given).isEqualTo(2);
    }

    @Test
    void knowsARepeatAcrossARestart(@TempDir Path worktree) {
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        var delay = new MasterReview.Said("qa", new Finding("Foo.java", "the delay is never pinned", "", "blocking",
                "delay", "test"));
        new MasterDecisions(mock(WorktreeChanges.class)).returned(task, 1L, List.of(delay));

        var repeated = new MasterDecisions(mock(WorktreeChanges.class)).returned(task, 2L, List.of(delay));

        assertThat(repeated).containsExactly(delay.identity());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Foo.java  | delay  | test      | the delay is never pinned | true
            Foo.java  | delay  | test      | no test applies the delay | true
            Foo.java  | Delay  | test      | the delay is never pinned | true
            Foo.java  | delay  | behaviour | the delay is never pinned | false
            Bar.java  | delay  | test      | the delay is never pinned | false
            Foo.java  | lock   | test      | the delay is never pinned | false
            """)
    void repeatsAFindingOnlyUnderTheSameFileAnchorAndKindWhateverItsWords(String file, String anchor, String kind,
                                                                           String issue, boolean repeats,
                                                                           @TempDir Path worktree) {
        MasterDecisions decisions = new MasterDecisions(mock(WorktreeChanges.class));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        decisions.returned(task, 1L, List.of(new MasterReview.Said("qa",
                new Finding("Foo.java", "every test injects zero", "", "blocking", "delay", "test"))));
        var now = new MasterReview.Said("developer", new Finding(file, issue, "", "blocking", anchor, kind));

        var returned = decisions.returned(task, 2L, List.of(now));

        assertThat(returned.contains(now.identity())).isEqualTo(repeats);
    }

    @Test
    void neverCountsAnEarlierRequestForEvidenceAsSettled(@TempDir Path worktree) {
        MasterDecisions decisions = new MasterDecisions(mock(WorktreeChanges.class));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
        var show = new MasterReview.Said("qa", new Finding("Foo.java", "which caller reads v2", "", "unproven", "",
                "behaviour"));
        decisions.returned(task, 1L, List.of(show));

        var returned = decisions.returned(task, 2L, List.of(show));

        assertThat(returned).isEmpty();
    }
}

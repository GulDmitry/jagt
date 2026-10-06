package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterDecisionsTest {

    @Test
    void stillAnswersANewQuestionOverATreeOnlyAnsweredOnceBefore() {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        MasterDecisions decisions = new MasterDecisions(changes);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("abc:1;"));

        decisions.answered(task);

        assertThat(decisions.answersSpentOnThisTree(task)).isFalse();
    }

    @Test
    void leavesTheNextQuestionToTheHumanAfterThreeAnswersChangedNothingInTheTree() {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        MasterDecisions decisions = new MasterDecisions(changes);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("abc:1;"));

        decisions.answered(task);
        decisions.answered(task);
        decisions.answered(task);

        assertThat(decisions.answersSpentOnThisTree(task)).isTrue();
    }

    @Test
    void answersAgainOnceTheTreeChanged() {
        WorktreeChanges changes = mock(WorktreeChanges.class);
        MasterDecisions decisions = new MasterDecisions(changes);
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(changes.state(task)).thenReturn(Optional.of("abc:1;"), Optional.of("abc:1;"), Optional.of("abc:1;"),
                Optional.of("abc:2;"));

        decisions.answered(task);
        decisions.answered(task);
        decisions.answered(task);

        assertThat(decisions.answersSpentOnThisTree(task)).isFalse();
    }
}

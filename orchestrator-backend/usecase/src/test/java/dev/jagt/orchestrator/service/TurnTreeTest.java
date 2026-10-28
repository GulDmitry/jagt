package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.Turn;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TurnTreeTest {

    private final WorktreeChanges changes = mock(WorktreeChanges.class);
    private final TaskTurns turns = mock(TaskTurns.class);
    private final TaskState task = TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();

    @Test
    void secondEndOnTheSameTreePasses() {
        when(changes.state(task)).thenReturn(Optional.of("h:1"));
        when(turns.of("ABC-1")).thenReturn(Turn.none().withMark("turn-end:tree", "h:1"));

        assertThat(new TurnTree(changes, turns).changedUnreported("ABC-1", task)).isFalse();
    }

    @Test
    void aTreeEditedSinceTheLastEndIsChanged() {
        when(changes.state(task)).thenReturn(Optional.of("h:2"));
        when(turns.of("ABC-1")).thenReturn(Turn.none().withMark("turn-end:tree", "h:1"));

        assertThat(new TurnTree(changes, turns).changedUnreported("ABC-1", task)).isTrue();
        verify(turns).mark("ABC-1", "turn-end:tree", "h:2");
    }

    @Test
    void theFirstEndWithUncommittedWorkIsChanged() {
        when(changes.state(task)).thenReturn(Optional.of("h:1"));
        when(turns.of("ABC-1")).thenReturn(Turn.none());
        when(changes.anyUncommitted(task)).thenReturn(true);

        assertThat(new TurnTree(changes, turns).changedUnreported("ABC-1", task)).isTrue();
        verify(turns).mark("ABC-1", "turn-end:tree", "h:1");
    }

    @Test
    void theFirstEndOnACleanTreeIsNotChanged() {
        when(changes.state(task)).thenReturn(Optional.of("h:1"));
        when(turns.of("ABC-1")).thenReturn(Turn.none());

        assertThat(new TurnTree(changes, turns).changedUnreported("ABC-1", task)).isFalse();
    }

    @Test
    void aTreeGitCannotReadIsNotChangedAndNotMarked() {
        when(changes.state(task)).thenReturn(Optional.empty());

        assertThat(new TurnTree(changes, turns).changedUnreported("ABC-1", task)).isFalse();
        verify(turns, never()).mark(anyString(), anyString(), anyString());
    }
}

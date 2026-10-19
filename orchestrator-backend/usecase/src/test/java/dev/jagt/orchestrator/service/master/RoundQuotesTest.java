package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoundQuotesTest {

    private final RoundFacts facts = mock(RoundFacts.class);
    private final MasterReview reviews = new MasterReview();
    private final RoundQuotes quotes = new RoundQuotes(facts, mock(MasterDecisions.class), reviews);

    @Test
    void keepsAReadyVerdictWhenOnlyTheBaseMovedUnderTheTasksOwnDiff(@TempDir Path worktree) throws Exception {
        Path file = worktree.resolve(MasterReview.FILE);
        Files.writeString(file, MasterReview.OWN_DIFF + "abc123\nnothing wrong\nVERDICT: ready\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(System.currentTimeMillis()).build();
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));

        boolean carried = quotes.carried(task);

        assertThat(carried).isTrue();
        assertThat(reviews.readsTheRoundInFront(task)).isTrue();
    }

    @Test
    void readsTheRoundAnewWhenTheTasksOwnDiffChanged(@TempDir Path worktree) throws Exception {
        Path file = worktree.resolve(MasterReview.FILE);
        Files.writeString(file, MasterReview.OWN_DIFF + "abc123\nnothing wrong\nVERDICT: ready\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(System.currentTimeMillis()).build();
        when(facts.ownDiff(task)).thenReturn(Optional.of("def456"));

        boolean carried = quotes.carried(task);

        assertThat(carried).isFalse();
        assertThat(reviews.readsTheRoundInFront(task)).isFalse();
    }

    @Test
    void readsANotReadyVerdictAgainWhenTheSessionRepliedWithTheSameOwnDiff(@TempDir Path worktree) throws Exception {
        Path file = worktree.resolve(MasterReview.FILE);
        Files.writeString(file, MasterReview.OWN_DIFF + "abc123\nFoo.java drops v2\nVERDICT: not ready\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
        TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).alias("a1")
                .lastActiveTimestamp(System.currentTimeMillis()).build();
        when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));

        boolean carried = quotes.carried(task);

        assertThat(carried).isFalse();
    }
}

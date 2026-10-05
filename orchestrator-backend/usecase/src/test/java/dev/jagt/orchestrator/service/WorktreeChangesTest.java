package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorktreeChangesTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final GitService gitService = mock(GitService.class);

    @Test
    void readsARepositoryGitCannotAnswerForAsHoldingWorkSoNoShipPassesItOver() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(gitService.hasUncommittedChanges(any(Path.class), any(Path.class)))
                .thenThrow(new IllegalStateException("git status in /wt failed (exit 128): not a git repository"));

        boolean holdsWork = new WorktreeChanges(configService, gitService).holdsWork("demo", "/wt", "main");

        assertThat(holdsWork).isTrue();
    }

    @Test
    void findsNothingToShipWhereNoRepositoryHoldsWorkAndNoneIsInReview() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));

        boolean anyToShip = new WorktreeChanges(configService, gitService)
                .anyToShip(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(anyToShip).isFalse();
    }

    @Test
    void namesWhyARepositorysDiffCouldNotBeReadRatherThanQuotingNothing() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(gitService.changesSince(any(Path.class), any(Path.class), any(String.class)))
                .thenThrow(new IllegalStateException("git merge-base in /wt failed (exit 1)"));

        String diff = new WorktreeChanges(configService, gitService)
                .diff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(diff).isEqualTo("# demo\njagt could not read it: git merge-base in /wt failed (exit 1)\n");
    }

    @Test
    void quotesNoDiffTooLargeForAPromptSoEachRoleReadsItItself() {
        when(configService.project("demo")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", List.of()));
        when(gitService.changesSince(any(Path.class), any(Path.class), any(String.class)))
                .thenReturn("+x".repeat(50_001));

        String diff = new WorktreeChanges(configService, gitService)
                .diff(TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build());

        assertThat(diff).isEmpty();
    }
}

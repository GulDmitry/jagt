package dev.jagt.orchestrator.capability.done;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.GitWorktrees;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RetiredWorktreesTest {

    private final GitWorktrees git = mock(GitWorktrees.class);
    private final EditorDriver editor = mock(EditorDriver.class);
    private final AgentSessions sessions = mock(AgentSessions.class);

    @Test
    void leavesTheWorktreeOnDiskWhenItsProjectIsGoneFromConfig() {
        boolean missing = new RetiredWorktrees(git, editor, sessions)
                .remove("ABC-1", List.of(TaskRepo.of("gone", "/wt")), Map.of());

        assertThat(missing).isTrue();
        verify(git, never()).removeWorktree(any(), any(), any());
    }

    @Test
    void dropsTheDeadWorktreeFromTheEditorsRecentProjects() {
        new RetiredWorktrees(git, editor, sessions).remove("ABC-1", List.of(TaskRepo.of("demo", "/wt")),
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())));

        verify(editor).forgetProject(Path.of("/wt"));
    }

    @Test
    void deletesTheWorktreeOfEveryRepositoryTheTaskWorkedIn() {
        new RetiredWorktrees(git, editor, sessions).remove("ABC-1",
                List.of(TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")), Map.of(
                        "api", new ProjectConfig("/api-repo", "origin/main", "dev", List.of()),
                        "web", new ProjectConfig("/web-repo", "origin/main", "dev", List.of())));

        verify(git).removeWorktree(Path.of("/api-repo"), Path.of("/api-wt"), null);
        verify(git).removeWorktree(Path.of("/web-repo"), Path.of("/web-wt"), null);
    }

    @Test
    void deletesTheThrowawayDiffCheckoutsOfTheRetiredTask() {
        new RetiredWorktrees(git, editor, sessions).remove("ABC-1", List.of(TaskRepo.of("demo", "/wt")),
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())));

        verify(git).removeDiffWorktrees(Path.of("/repo"), "ABC-1", "demo");
    }

    @Test
    void hasTheAgentRuntimeUndoWhatItWroteOutsideTheRetiredWorktree() {
        new RetiredWorktrees(git, editor, sessions).remove("ABC-1", List.of(TaskRepo.of("demo", "/wt")),
                Map.of("demo", new ProjectConfig("/repo", "origin/main", "dev", List.of())));

        verify(sessions).forgetWorktree(Path.of("/wt"));
    }
}

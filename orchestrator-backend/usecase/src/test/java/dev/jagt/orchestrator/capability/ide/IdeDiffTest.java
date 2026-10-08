package dev.jagt.orchestrator.capability.ide;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.DiffCheckouts;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdeDiffTest {

    private final EditorDriver editor = mock(EditorDriver.class);
    private final ConfigService config = mock(ConfigService.class);
    private final DiffCheckouts diffs = mock(DiffCheckouts.class);

    @Test
    void showsTheChangeAgainstTheBaseBranchWhenTheHumanAsksForADiff() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/main", "dev", null));
        when(diffs.checkoutBaseForDiff(any(), any(), any(), any())).thenReturn(Path.of("/tmp/base"));
        when(diffs.checkoutWorktreeCleanForDiff(any(), any(), any(), any(), any())).thenReturn(Path.of("/tmp/clean"));

        new IdeDiff(config, diffs, editor).open("ABC-1", task);

        verify(editor).openDiff(Path.of("/tmp/base"), Path.of("/tmp/clean"));
    }

    @Test
    void diffsAgainstWhatTheRequestTargetsRatherThanWhereDeployLands() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(config.project("proj")).thenReturn(new ProjectConfig("/repo", "origin/release/stage", "dev", null));
        when(diffs.checkoutBaseForDiff(any(), any(), any(), any())).thenReturn(Path.of("/tmp/base"));
        when(diffs.checkoutWorktreeCleanForDiff(any(), any(), any(), any(), any())).thenReturn(Path.of("/tmp/clean"));

        new IdeDiff(config, diffs, editor).open("ABC-1", task);

        verify(diffs).checkoutBaseForDiff(Path.of("/repo"), "origin/release/stage", "ABC-1", "proj");
    }

    @Test
    void diffsEveryRepositoryAgainstItsOwnTargetWhenTheTaskSpansTwoProjects() {
        TaskState task = TaskState.builder(List.of(
                        TaskRepo.of("api", "/api-wt"), TaskRepo.of("web", "/web-wt")),
                TaskStatus.REVIEW_PENDING).build();
        when(config.project("api")).thenReturn(new ProjectConfig("/api-repo", "origin/main", "dev", null));
        when(config.project("web")).thenReturn(new ProjectConfig("/web-repo", "origin/next", "dev", null));
        when(diffs.checkoutBaseForDiff(any(), any(), any(), any())).thenReturn(Path.of("/tmp/base"));
        when(diffs.checkoutWorktreeCleanForDiff(any(), any(), any(), any(), any())).thenReturn(Path.of("/tmp/clean"));

        new IdeDiff(config, diffs, editor).open("ABC-1", task);

        verify(diffs).checkoutBaseForDiff(Path.of("/api-repo"), "origin/main", "ABC-1", "api");
        verify(diffs).checkoutBaseForDiff(Path.of("/web-repo"), "origin/next", "ABC-1", "web");
    }
}

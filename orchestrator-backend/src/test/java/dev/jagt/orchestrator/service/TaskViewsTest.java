package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskViewsTest {

    private final StateService stateService = mock(StateService.class);

    @Test
    void announcesTheDraftedRepliesWaitingInTheWorktree(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve("review_replies.md"), "> rename x\n\nRenamed it.\n");
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));

        var views = new TaskViews(stateService).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(true);
    }

    @Test
    void announcesNoDraftedRepliesWhenTheAgentHasWrittenNone(@TempDir Path worktree) {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));

        var views = new TaskViews(stateService).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(false);
    }

    @Test
    void survivesATaskWhoseWorktreeIsAlreadyGone() {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", "/nowhere/ABC-1-proj", TaskStatus.DONE).build()));

        var views = new TaskViews(stateService).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(false);
    }
}

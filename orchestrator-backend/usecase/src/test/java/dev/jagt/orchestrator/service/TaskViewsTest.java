package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
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
    private final ConfigService configService = mock(ConfigService.class);

    @Test
    void announcesTheDraftedRepliesWaitingInTheWorktree(@TempDir Path worktree) throws Exception {
        Files.writeString(worktree.resolve("review_replies.md"), "> rename x\n\nRenamed it.\n");
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

        var views = new TaskViews(stateService, configService, new MasterReview()).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(true);
    }

    @Test
    void announcesNoDraftedRepliesWhenTheAgentHasWrittenNone(@TempDir Path worktree) {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

        var views = new TaskViews(stateService, configService, new MasterReview()).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(false);
    }

    @Test
    void showsARoundTheMasterHasYetToReadAsItsReview(@TempDir Path worktree) {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

        var views = new TaskViews(stateService, configService, new MasterReview()).all();

        assertThat(views).singleElement().extracting("statusLabel").isEqualTo("master review");
    }

    @Test
    void keepsRenderingWhenTheConfigurationCannotBeRead() {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", "/nowhere/ABC-1-proj", TaskStatus.CI_POLLING).build()));
        when(configService.load()).thenThrow(new IllegalStateException("jagt.yml is not valid YAML"));

        var snapshot = new TaskViews(stateService, configService, new MasterReview()).snapshot();

        assertThat(snapshot.tasks()).hasSize(1);
        assertThat(snapshot.cadence().enabled()).isFalse();
        assertThat(snapshot.projects()).isEmpty();
    }

    @Test
    void survivesATaskWhoseWorktreeIsAlreadyGone() {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", "/nowhere/ABC-1-proj", TaskStatus.DONE).build()));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

        var views = new TaskViews(stateService, configService, new MasterReview()).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(false);
    }
}

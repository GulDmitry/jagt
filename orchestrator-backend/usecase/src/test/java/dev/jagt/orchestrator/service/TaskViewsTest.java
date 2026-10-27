package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import org.assertj.core.api.InstanceOfAssertFactories;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        var views = new TaskViews(stateService, configService, new Rounds(configService, new MasterReview(stateService)), Clock.systemUTC()).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(true);
    }

    @Test
    void announcesNoDraftedRepliesWhenTheAgentHasWrittenNone(@TempDir Path worktree) {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

        var views = new TaskViews(stateService, configService, new Rounds(configService, new MasterReview(stateService)), Clock.systemUTC()).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(false);
    }

    @Test
    void showsARoundTheMasterHasYetToReadAsItsReview(@TempDir Path worktree) {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

        var views = new TaskViews(stateService, configService, new Rounds(configService, new MasterReview(stateService)), Clock.systemUTC()).all();

        assertThat(views).singleElement().extracting("statusLabel").isEqualTo("master review");
    }

    @Test
    void keepsRenderingWhenTheConfigurationCannotBeRead() {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", "/nowhere/ABC-1-proj", TaskStatus.CI_POLLING).build()));
        when(configService.load()).thenThrow(new IllegalStateException("jagt.yml is not valid YAML"));

        var snapshot = new TaskViews(stateService, configService, new Rounds(configService, new MasterReview(stateService)), Clock.systemUTC()).snapshot();

        assertThat(snapshot.tasks()).hasSize(1);
        assertThat(snapshot.cadence().enabled()).isFalse();
        assertThat(snapshot.projects()).isEmpty();
    }

    @Test
    void survivesATaskWhoseWorktreeIsAlreadyGone() {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", "/nowhere/ABC-1-proj", TaskStatus.DONE).build()));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

        var views = new TaskViews(stateService, configService, new Rounds(configService, new MasterReview(stateService)), Clock.systemUTC()).all();

        assertThat(views).singleElement().extracting("draftedReplies").isEqualTo(false);
    }

    @Test
    void listsEachTaskOnOneLineWithItsAliasAndLegalVerbsButNotItsHistory() {
        TaskState task = TaskState.builder("alpha", "/wt/ABC-1", TaskStatus.REVIEW_PENDING).alias("a1")
                .title("Fix the login").message("ready").mrUrl("https://host/mr/7")
                .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1L, ActionOrigin.BOARD))).build();
        when(stateService.tasks()).thenReturn(Map.of("ABC-1", task));
        when(stateService.task("ABC-1")).thenReturn(Optional.of(task));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

        String listing = new TaskViews(stateService, configService, new Rounds(configService, new MasterReview(stateService)), Clock.systemUTC())
                .listing();

        assertThat(listing).isEqualTo("ABC-1 [REVIEW_PENDING] Fix the login — ready | /wt/ABC-1 https://host/mr/7"
                + " alias=a1 legal=ship,sweep,deploy,done,focus,ide,diff,respawn");
    }

    @Test
    void saysThereAreNoTasksWhenNoneIsOpen() {
        when(stateService.tasks()).thenReturn(Map.of());
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());

        String listing = new TaskViews(stateService, configService, new Rounds(configService, new MasterReview(stateService)), Clock.systemUTC())
                .listing();

        assertThat(listing).isEqualTo("no tasks");
    }

    @Test
    void asksForTheDeployConfirmationNamingEachRepositorysDeployBranch() {
        when(stateService.tasks()).thenReturn(Map.of("ABC-1",
                TaskState.builder("proj", "/wt/ABC-1", TaskStatus.REVIEW_PENDING).build()));
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults().withProjects(Map.of("proj",
                new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

        var views = new TaskViews(stateService, configService, new Rounds(configService, new MasterReview(stateService)), Clock.systemUTC()).all();

        assertThat(views).singleElement().extracting("confirmations").asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("deploy", "Deploy ABC-1?\n\nThis merges and pushes:\nproj → dev");
    }
}

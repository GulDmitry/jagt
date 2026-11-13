package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskViewsTest {

  @TempDir Path worktree;

  private final StateService stateService = mock(StateService.class);
  private final ConfigService configService = mock(ConfigService.class);
  private TaskViews views;

  @BeforeEach
  void setUp() {
    views = TestTaskViewsBuilder.taskViews(stateService, configService).build();
    when(stateService.tasks())
        .thenReturn(
            Map.of(
                "ABC-1",
                TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build()));
    when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults());
  }

  @Test
  void announcesTheDraftedRepliesWaitingInTheWorktree() throws Exception {
    Files.writeString(worktree.resolve("review_replies.md"), "> rename x\n\nRenamed it.\n");

    assertThat(views.all()).singleElement().extracting("draftedReplies").isEqualTo(true);
  }

  @Test
  void announcesNoDraftedRepliesWhenTheAgentHasWrittenNone() {
    assertThat(views.all()).singleElement().extracting("draftedReplies").isEqualTo(false);
  }

  @Test
  void carriesWhenTheDraftedRepliesWereLastWritten() throws Exception {
    Path replies =
        Files.writeString(worktree.resolve("review_replies.md"), "> rename x\n\nRenamed it.\n");
    Files.setLastModifiedTime(replies, FileTime.fromMillis(1_700_000_000_000L));

    assertThat(views.all())
        .singleElement()
        .extracting(view -> view.replies().repliesWrittenAt())
        .isEqualTo(1_700_000_000_000L);
  }

  @Test
  void carriesNoWriteTimeWhenTheAgentHasWrittenNoReplies() {
    assertThat(views.all())
        .singleElement()
        .extracting(view -> view.replies().repliesWrittenAt())
        .isEqualTo(0L);
  }

  @Test
  void showsRoundTheMasterHasYetToReadAsItsReview() {
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

    assertThat(views.all()).singleElement().extracting("statusLabel").isEqualTo("master review");
  }

  @Test
  void keepsRenderingWhenTheConfigurationCannotBeRead() {
    when(stateService.tasks())
        .thenReturn(
            Map.of(
                "ABC-1",
                TaskState.builder("proj", "/nowhere/ABC-1-proj", TaskStatus.CI_POLLING).build()));
    when(configService.load()).thenThrow(new IllegalStateException("jagt.yml is not valid YAML"));

    var snapshot = views.snapshot();

    assertThat(snapshot.tasks()).hasSize(1);
    assertThat(snapshot.cadence().enabled()).isFalse();
    assertThat(snapshot.projects()).isEmpty();
  }

  @Test
  void survivesTaskWhoseWorktreeIsAlreadyGone() {
    when(stateService.tasks())
        .thenReturn(
            Map.of(
                "ABC-1",
                TaskState.builder("proj", "/nowhere/ABC-1-proj", TaskStatus.DONE).build()));

    assertThat(views.all()).singleElement().extracting("draftedReplies").isEqualTo(false);
  }

  @Test
  void listsEachTaskOnOneLineWithItsAliasAndLegalVerbsButNotItsHistory() {
    TaskState task =
        TaskState.builder("alpha", "/wt/ABC-1", TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .title("Fix the login")
            .message("ready")
            .mrUrl("https://host/mr/7")
            .history(List.of(new StatusChange(TaskStatus.IN_PROGRESS, 1L, ActionOrigin.BOARD)))
            .build();
    when(stateService.tasks()).thenReturn(Map.of("ABC-1", task));
    when(stateService.task("ABC-1")).thenReturn(Optional.of(task));

    assertThat(views.listing())
        .isEqualTo(
            "ABC-1 [REVIEW_PENDING] Fix the login — ready | /wt/ABC-1 https://host/mr/7"
                + " alias=a1 legal=ship,sweep,deploy,pause,done,focus,ide,diff,respawn");
  }

  @Test
  void listsScenariosAmongTheLegalVerbsWhereAcceptanceIsReady() {
    TaskState task = TaskState.builder("alpha", "/wt/ABC-1", TaskStatus.REVIEW_PENDING).build();
    when(stateService.tasks()).thenReturn(Map.of("ABC-1", task));
    when(stateService.task("ABC-1")).thenReturn(Optional.of(task));
    TaskViews ready =
        TestTaskViewsBuilder.taskViews(stateService, configService)
            .withScenarios(() -> true)
            .build();

    assertThat(ready.listing()).contains("pause,done,scenarios,focus");
  }

  @Test
  void saysThereAreNoTasksWhenNoneIsOpen() {
    when(stateService.tasks()).thenReturn(Map.of());

    assertThat(views.listing()).isEqualTo("no tasks");
  }

  @Test
  void asksForTheDeployConfirmationNamingEachRepositorysDeployBranch() {
    when(stateService.tasks())
        .thenReturn(
            Map.of(
                "ABC-1",
                TaskState.builder("proj", "/wt/ABC-1", TaskStatus.REVIEW_PENDING).build()));
    when(configService.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withProjects(
                    Map.of("proj", new ProjectConfig("/repo", "origin/main", "dev", List.of()))));

    assertThat(views.all())
        .singleElement()
        .extracting("confirmations")
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsEntry("deploy", "Deploy ABC-1?\n\nThis merges and pushes:\nproj → dev");
  }
}

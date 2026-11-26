package dev.jagt.orchestrator.job.review;

import static dev.jagt.orchestrator.job.TestYourMoveTaskBuilder.yourMoveTask;
import static dev.jagt.orchestrator.job.review.TestYourMoveJobBuilder.yourMoveJob;
import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static dev.jagt.orchestrator.service.master.TestMasterReviewBuilder.bareMasterReview;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.channel.Notification;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class YourMoveJobTest {

  private final Notifications notifications = mock(Notifications.class);
  private final WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
  private final ConfigService configService = mock(ConfigService.class);
  private final FakeClock clock = new FakeClock();

  @TempDir Path root;

  private StateService state;
  private MasterReview reviews;
  private String worktree;
  private YourMoveJob job;

  @BeforeEach
  void youWatchingAnEmptyState() throws IOException {
    state = stateService().withRoot(root).build();
    reviews = new MasterReview(state);
    Files.createDirectories(root.resolve("wt"));
    worktree = root.resolve("wt").toString();
    when(configService.load()).thenReturn(yourMoveJob().config());
    job = yourMoveJob().build(state, notifications, worktreeChanges, configService, clock);
  }

  @Test
  void tapsYouWhenSessionHandsRoundBackAndNoMasterReadsIt() {
    state.putTask("ABC-1", yourMoveTask().build());
    job.run();
    state.putTask("ABC-1", yourMoveTask().withStatus(TaskStatus.REVIEW_PENDING).build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verify(notifications)
        .send(
            argThat(
                sent -> "ABC-1".equals(sent.taskId()) && "review_pending".equals(sent.title())));
  }

  @Test
  void tapsYouWhenSessionStopsToAskWithoutLeavingTheStatusItWorkedIn() {
    state.putTask("ABC-1", yourMoveTask().withMessage("step 2").build());
    job.run();
    state.putTask(
        "ABC-1",
        yourMoveTask()
            .withMessage("which uniqueness rule")
            .withReport(AgentReport.QUESTION)
            .build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verify(notifications).send(argThat(sent -> "needs input".equals(sent.title())));
  }

  @Test
  void tapsYouOnceForMoveSeenOnManyTicks() {
    state.putTask("ABC-1", yourMoveTask().build());
    job.run();
    state.putTask("ABC-1", yourMoveTask().withStatus(TaskStatus.REVIEW_PENDING).build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();
    job.run();
    job.run();

    verify(notifications, times(1)).send(any());
    verify(notifications).send(argThat(sent -> "ABC-1".equals(sent.taskId())));
  }

  @Test
  void staysQuietOnKeepAliveWhileTheSessionWorks() {
    state.putTask("ABC-1", yourMoveTask().withMessage("step 1").build());
    job.run();
    state.putTask("ABC-1", yourMoveTask().withMessage("step 2").build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verifyNoInteractions(notifications);
  }

  @Test
  void staysQuietAboutTaskAlreadyYoursWhenJagtStartsUp() {
    state.putTask("ABC-1", yourMoveTask().withStatus(TaskStatus.REVIEW_PENDING).build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verifyNoInteractions(notifications);
  }

  @Test
  void staysQuietAboutTaskThatLeftAndCameBackAlreadyYours() {
    state.putTask("ABC-1", yourMoveTask().build());
    job.run();
    state.removeTask("ABC-1");
    job.run();
    state.putTask("ABC-1", yourMoveTask().withStatus(TaskStatus.REVIEW_PENDING).build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verifyNoInteractions(notifications);
  }

  @Test
  void tapsYouWhenTheReviewApprovesTheRound() {
    state.putTask(
        "ABC-1", yourMoveTask().withStatus(TaskStatus.CI_POLLING).withMrUrl("http://mr/1").build());
    job.run();
    state.putTask(
        "ABC-1",
        yourMoveTask()
            .withStatus(TaskStatus.APPROVED)
            .withMrUrl("http://mr/1")
            .withPipelineStatus("success")
            .build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verify(notifications)
        .send(argThat(sent -> "ABC-1".equals(sent.taskId()) && sent.body().contains("approved")));
  }

  @Test
  void tapsYouWhenTheChecksFailedAndTheRoundIsYoursToRelay() {
    state.putTask(
        "ABC-1", yourMoveTask().withStatus(TaskStatus.CI_POLLING).withMrUrl("http://mr/1").build());
    job.run();
    state.putTask(
        "ABC-1", yourMoveTask().withStatus(TaskStatus.CI_FAILED).withMrUrl("http://mr/1").build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verify(notifications).send(argThat(sent -> "ci_failed".equals(sent.title())));
  }

  @Nested
  class WhereTheSessionHandedTheRoundBackInTheWorktree {

    @BeforeEach
    void roundHandedBack() {
      state.putTask("ABC-1", yourMoveTask().withWorktree(worktree).build());
      job.run();
      state.putTask(
          "ABC-1",
          yourMoveTask()
              .withWorktree(worktree)
              .withStatus(TaskStatus.REVIEW_PENDING)
              .withMessage("widget fixed")
              .build());
    }

    @Test
    void tellsYouAboutTheDraftedRepliesWaitingInTheWorktree() throws IOException {
      Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: done\n");

      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
      verify(notifications).send(sent.capture());
      assertThat(sent.getValue().body())
          .containsOnlyOnce("drafted replies")
          .contains("review_replies.md");
    }

    @Test
    void tellsYouDraftedRepliesWaitWhereTheRoundChangedNoCode() throws IOException {
      state.putTask(
          "ABC-1",
          yourMoveTask()
              .withWorktree(worktree)
              .withStatus(TaskStatus.REVIEW_PENDING)
              .withReport(AgentReport.NO_CHANGES)
              .build());
      Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: done\n");

      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
      verify(notifications).send(sent.capture());
      assertThat(sent.getValue().body()).contains("drafted replies wait");
    }

    @Test
    void tapsYouAgainWhenDraftedRepliesArriveUnderTheMoveYouWereToldOf() throws IOException {
      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();
      Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: done\n");

      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      verify(notifications).send(argThat(sent -> sent.body().contains("review_replies.md")));
    }
  }

  @Nested
  class WhereTheMasterJudges {

    @BeforeEach
    void masterJudging() {
      when(configService.load()).thenReturn(yourMoveJob().withMode("judge").config());
      job =
          yourMoveJob()
              .withMode("judge")
              .build(state, notifications, worktreeChanges, configService, clock);
    }

    @Test
    void staysQuietWhileTheMasterStillReadsTheHandBack() {
      state.putTask("ABC-1", yourMoveTask().build());
      job.run();
      state.putTask("ABC-1", yourMoveTask().withStatus(TaskStatus.REVIEW_PENDING).build());

      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      verifyNoInteractions(notifications);
    }
  }

  @Nested
  class WhereTheMasterActsAndKeepsNothingForYou {

    @BeforeEach
    void masterActing() {
      when(configService.load()).thenReturn(yourMoveJob().withMode("act").config());
      job =
          yourMoveJob()
              .withMode("act")
              .build(state, notifications, worktreeChanges, configService, clock);
    }

    @Test
    void staysQuietWhenTheMasterAttachesTheRepositoryBeforeTheRequestSettles() {
      state.putTask("ABC-1", yourMoveTask().withMessage("step 1").build());
      job.run();
      state.putTask(
          "ABC-1",
          yourMoveTask()
              .withMessage("needs the registry")
              .withReport(AgentReport.QUESTION)
              .withRepoRequest("registry")
              .build());
      job.run();
      clock.advance(Duration.ofSeconds(2));
      state.putTask("ABC-1", yourMoveTask().withMessage("repository added").build());

      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      verifyNoInteractions(notifications);
    }
  }

  @Nested
  class WhereYouKeptApprovingPlans {

    @BeforeEach
    void masterActingWhileYouKeepPlans() {
      when(configService.load())
          .thenReturn(yourMoveJob().withMode("act").withMine(List.of("plan")).config());
      job =
          yourMoveJob()
              .withMode("act")
              .withMine(List.of("plan"))
              .build(state, notifications, worktreeChanges, configService, clock);
    }

    @Test
    void staysQuietWhileTheMasterReadsPlan() {
      state.putTask("ABC-1", yourMoveTask().build());
      job.run();
      state.putTask("ABC-1", yourMoveTask().withStatus(TaskStatus.PLAN_PENDING).build());

      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      verifyNoInteractions(notifications);
    }

    @Test
    void staysQuietWhenTheMasterAnswersQuestionBeforeItSettles() {
      state.putTask("ABC-1", yourMoveTask().withMessage("step 2").build());
      job.run();
      state.putTask(
          "ABC-1",
          yourMoveTask()
              .withMessage("which uniqueness rule")
              .withReport(AgentReport.QUESTION)
              .build());
      job.run();
      clock.advance(Duration.ofSeconds(1));
      state.putTask("ABC-1", yourMoveTask().withMessage("master answered the question").build());
      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      verifyNoInteractions(notifications);
    }

    @Test
    void staysQuietWhileTheMasterAnswersQuestionThatOutlivesTheSettle() {
      state.putTask("ABC-1", yourMoveTask().withMessage("step 2").build());
      job.run();
      state.putTask(
          "ABC-1",
          yourMoveTask()
              .withMessage("which uniqueness rule")
              .withReport(AgentReport.QUESTION)
              .build());
      job.run();
      clock.advance(Duration.ofSeconds(30));
      job.run();

      verifyNoInteractions(notifications);
    }

    @Test
    void tapsYouOnceTheMasterLeavesQuestionUnanswered() {
      state.putTask("ABC-1", yourMoveTask().withMessage("step 2").build());
      job.run();
      state.putTask(
          "ABC-1",
          yourMoveTask()
              .withMessage("which uniqueness rule")
              .withReport(AgentReport.QUESTION)
              .build()
              .withMaster(
                  Master.defaults().withUnanswered(Turn.digestOf("which uniqueness rule"))));
      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      verify(notifications, times(1)).send(argThat(sent -> "needs input".equals(sent.title())));
    }

    @Test
    void staysQuietWhenTheMasterDeploysAnApprovedTaskBeforeItSettles() {
      state.putTask(
          "ABC-1",
          yourMoveTask().withStatus(TaskStatus.CI_POLLING).withMrUrl("http://mr/1").build());
      job.run();
      state.putTask(
          "ABC-1", yourMoveTask().withStatus(TaskStatus.APPROVED).withMrUrl("http://mr/1").build());
      job.run();
      clock.advance(Duration.ofSeconds(1));
      state.putTask(
          "ABC-1", yourMoveTask().withStatus(TaskStatus.DEPLOYED).withMrUrl("http://mr/1").build());
      job.run();
      clock.advance(Duration.ofSeconds(10));
      job.run();

      verifyNoInteractions(notifications);
    }
  }

  @ParameterizedTest
  @CsvSource({"plan, PLAN_PENDING, plan_pending", "ship, REVIEW_PENDING, review_pending"})
  void tapsYouOnceTheMastersReadyVerdictLeavesTheRoundToYouWhenYouKeptItsMove(
      String kept, TaskStatus status, String title) {
    TestYourMoveJobBuilder keeping = yourMoveJob().withMode("act").withMine(List.of(kept));
    when(configService.load()).thenReturn(keeping.config());
    job = keeping.build(state, notifications, worktreeChanges, configService, clock);
    state.putTask("ABC-1", yourMoveTask().withWorktree(worktree).withStatus(status).build());
    job.run();
    TaskState judged = state.task("ABC-1").orElseThrow();
    reviews.write(
        "ABC-1", judged, bareMasterReview().withRound(judged.standing().statusSince()).build(), "");

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();
    job.run();

    verify(notifications, times(1))
        .send(argThat(sent -> "ABC-1".equals(sent.taskId()) && title.equals(sent.title())));
  }

  @ParameterizedTest
  @CsvSource({
    "plan, REVIEW_PENDING, SHIPPING",
    "reply, PLAN_PENDING, IN_PROGRESS",
  })
  void neverTapsYouForRoundTheMasterMovesOnBeforeTheMoveSettles(
      String kept, TaskStatus judgedAt, TaskStatus movedTo) {
    TestYourMoveJobBuilder keeping = yourMoveJob().withMode("act").withMine(List.of(kept));
    when(configService.load()).thenReturn(keeping.config());
    job = keeping.build(state, notifications, worktreeChanges, configService, clock);
    state.putTask("ABC-1", yourMoveTask().withWorktree(worktree).withStatus(judgedAt).build());
    job.run();
    TaskState judged = state.task("ABC-1").orElseThrow();
    reviews.write(
        "ABC-1", judged, bareMasterReview().withRound(judged.standing().statusSince()).build(), "");

    job.run();
    clock.advance(Duration.ofSeconds(1));
    state.putTask("ABC-1", yourMoveTask().withWorktree(worktree).withStatus(movedTo).build());
    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verifyNoInteractions(notifications);
  }

  @Test
  void tapsYouOnceForQuestionWhenYouKeptAnswering() {
    when(configService.load())
        .thenReturn(yourMoveJob().withMode("act").withMine(List.of("answer")).config());
    job =
        yourMoveJob()
            .withMode("act")
            .withMine(List.of("answer"))
            .build(state, notifications, worktreeChanges, configService, clock);
    state.putTask("ABC-1", yourMoveTask().withMessage("step 2").build());
    job.run();
    state.putTask(
        "ABC-1",
        yourMoveTask()
            .withMessage("which uniqueness rule")
            .withReport(AgentReport.QUESTION)
            .build());
    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();
    job.run();

    verify(notifications, times(1)).send(argThat(sent -> "needs input".equals(sent.title())));
  }

  @Test
  void tapsYouOnceForAnApprovedTaskWhenYouKeptTheDeploy() {
    when(configService.load())
        .thenReturn(yourMoveJob().withMode("act").withMine(List.of("deploy", "revert")).config());
    job =
        yourMoveJob()
            .withMode("act")
            .withMine(List.of("deploy", "revert"))
            .build(state, notifications, worktreeChanges, configService, clock);
    state.putTask(
        "ABC-1",
        yourMoveTask()
            .withWorktree(worktree)
            .withStatus(TaskStatus.CI_POLLING)
            .withMrUrl("http://mr/1")
            .build());
    job.run();
    state.putTask(
        "ABC-1",
        yourMoveTask()
            .withWorktree(worktree)
            .withStatus(TaskStatus.APPROVED)
            .withMrUrl("http://mr/1")
            .withPipelineStatus("success")
            .build());
    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();
    job.run();

    verify(notifications, times(1)).send(argThat(sent -> "approved".equals(sent.title())));
  }

  @Test
  void namesTheDraftedRepliesWhenShipTheMasterMadeLeftThemForYou() throws IOException {
    when(configService.load())
        .thenReturn(yourMoveJob().withMode("act").withMine(List.of("reply", "ship")).config());
    job =
        yourMoveJob()
            .withMode("act")
            .withMine(List.of("reply", "ship"))
            .build(state, notifications, worktreeChanges, configService, clock);
    Files.writeString(root.resolve("wt/review_replies.md"), "to thread 1: done\n");
    state.putTask(
        "ABC-1",
        yourMoveTask()
            .withWorktree(worktree)
            .withStatus(TaskStatus.REVIEW_PENDING)
            .withMrUrl("http://mr/1")
            .build());
    job.run();
    TaskState drafted = state.task("ABC-1").orElseThrow();
    reviews.write(
        "ABC-1",
        drafted,
        bareMasterReview().withRound(drafted.standing().statusSince()).build(),
        "");

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();

    verify(notifications).send(argThat(sent -> sent.body().contains("review_replies.md")));
  }

  @Test
  void tapsYouOnceForRepositoryRequestYouKeptTheAttachingOf() {
    when(configService.load())
        .thenReturn(yourMoveJob().withMode("act").withMine(List.of("repo")).config());
    job =
        yourMoveJob()
            .withMode("act")
            .withMine(List.of("repo"))
            .build(state, notifications, worktreeChanges, configService, clock);
    state.putTask("ABC-1", yourMoveTask().withMessage("step 1").build());
    job.run();
    state.putTask(
        "ABC-1",
        yourMoveTask()
            .withMessage("needs the registry")
            .withReport(AgentReport.QUESTION)
            .withRepoRequest("registry")
            .build());

    job.run();
    clock.advance(Duration.ofSeconds(10));
    job.run();
    job.run();

    verify(notifications, times(1))
        .send(
            argThat(sent ->
                "needs input".equals(sent.title()) && sent.body().contains("another repository")));
  }
}

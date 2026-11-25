package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.turn.AgentReport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class FlowReportsTest {

  private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

  private final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  private final ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> write =
      ArgumentCaptor.captor();

  private final Deployment.Target mergedWeb =
      Deployment.Target.of("web", "dev").withMerge("0123456789abcdef");

  private final ReducingStore stateService = mock(ReducingStore.class);
  private final FlowReports reports =
      new FlowReports(
          stateService,
          CLOCK,
          new Checkers(new TaskChart(Composition.builtIn())),
          new FlowRules(new TaskChart(Composition.builtIn())));

  @Test
  void letsTaskSayItIsWaitingOnTheHuman() {
    when(stateService.reduce(eq("ABC-1"), any())).thenReturn(true);

    reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    assertThat(
            write
                .getValue()
                .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
                .standing()
                .status())
        .isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @Test
  void landsNoReportOnTaskWhoseStatusMovedOnSinceItWasRead() {
    TaskState read = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
    TaskState live = read.withStatus(TaskStatus.SHIPPING, null, 6_000);
    AtomicReference<TaskState> written = new AtomicReference<>();
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          written.set(write.apply(reducer, live));
          return true;
        });

    FlowReports.Claimed claimed =
        reports.reportWhileStanding(
            "ABC-1", read, TaskStatus.IN_PROGRESS, AgentReport.PLAIN, "not ready", task -> task);

    assertThat(claimed).isEqualTo(FlowReports.Claimed.MOVED_ON);
    assertThat(written.get()).isSameAs(live);
  }

  @Test
  void landsTheReportOnTaskStillWhereItWasRead() {
    TaskState read = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
    AtomicReference<TaskState> written = new AtomicReference<>();
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          written.set(write.apply(reducer, read));
          return true;
        });

    FlowReports.Claimed claimed =
        reports.reportWhileStanding(
            "ABC-1", read, TaskStatus.IN_PROGRESS, AgentReport.PLAIN, "not ready", task -> task);

    assertThat(claimed).isEqualTo(FlowReports.Claimed.LANDED);
    assertThat(written.get().standing().status()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void refusesToLetTaskTalkItselfOntoSharedBranch() {
    assertThatThrownBy(() -> reports.report("ABC-1", TaskStatus.DEPLOYED, "merged"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DEPLOYED")
        .hasMessageContaining("jagt's to set");
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVIEWED", "APPROVED"})
  void refusesSessionThatReportsItsOwnReviewVerdict(TaskStatus verdict) {
    assertThatThrownBy(() -> reports.report("ABC-1", verdict, "lgtm"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jagt's to set");
  }

  @Test
  void countsReportTheTasksStatusForbidsOnTheTask() {
    AtomicReference<TaskState> written = new AtomicReference<>();
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          written.set(
              write.apply(
                  reducer, TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build()));
          return true;
        });

    assertThatThrownBy(() -> reports.report("ABC-1", TaskStatus.PLAN_PENDING, "a plan"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(written.get().standing().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
    assertThat(written.get().gate().refusals())
        .containsExactly(Map.entry(RefusalKind.STATUS_REPORT, 1));
  }

  @Test
  void landsAnApprovalTheHostsRoundReadConcluded() {
    when(stateService.reduce(eq("ABC-1"), any())).thenReturn(true);

    reports.read("ABC-1", TaskStatus.APPROVED, "approved");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    assertThat(
            write
                .getValue()
                .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
                .standing()
                .status())
        .isEqualTo(TaskStatus.APPROVED);
  }

  @ParameterizedTest
  @EnumSource(
      value = TaskStatus.class,
      names = {"REVERTED", "DEPLOY_CONFLICT"})
  void keepsTheLineOfStatusHumanHoldsWhenItsSessionReports(TaskStatus held) {
    when(stateService.reduce(eq("ABC-1"), any())).thenReturn(true);

    reports.report("ABC-1", TaskStatus.IN_PROGRESS, AgentReport.NO_CHANGES, "clean worktree");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState after =
        write
            .getValue()
            .apply(
                reducer,
                TaskState.builder("proj", "/wt", held)
                    .message("deploy ABC-1: conflict in Foo.java")
                    .build());
    assertThat(after.standing().status()).isEqualTo(held);
    assertThat(after.standing().message()).isEqualTo("deploy ABC-1: conflict in Foo.java");
  }

  @Test
  void putsQuestionOnTheCardOfStatusHumanHolds() {
    when(stateService.reduce(eq("ABC-1"), any())).thenReturn(true);

    reports.report(
        "ABC-1", TaskStatus.IN_PROGRESS, AgentReport.QUESTION, "keep both sides of Foo.java?");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState after =
        write
            .getValue()
            .apply(
                reducer,
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
                    .message("deploy ABC-1: conflict in Foo.java")
                    .build());
    assertThat(after.standing().message()).isEqualTo("keep both sides of Foo.java?");
    assertThat(after.standing().report()).isEqualTo(AgentReport.QUESTION);
  }

  @Test
  void leavesTheLineToTheHeldStatusOnceItsQuestionIsBehind() {
    when(stateService.reduce(eq("ABC-1"), any())).thenReturn(true);

    reports.report("ABC-1", TaskStatus.IN_PROGRESS, "resolving the merge");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState after =
        write
            .getValue()
            .apply(
                reducer,
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
                    .message("keep both sides of Foo.java?")
                    .report(AgentReport.QUESTION)
                    .build());
    assertThat(after.standing().message()).isNull();
    assertThat(after.standing().report()).isEqualTo(AgentReport.PLAIN);
  }

  @Test
  void recordsTheRequestLinkInTheSameWriteAsTheStatusThatNeedsIt() {
    when(stateService.reduce(eq("ABC-1"), any())).thenReturn(true);

    reports.report(
        "ABC-1",
        TaskStatus.CI_POLLING,
        AgentReport.PLAIN,
        "review request: http://host/1",
        (was, task) -> task.withMrUrl("http://host/1", 2_000));

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState after =
        write
            .getValue()
            .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build());
    assertThat(after.standing().status()).isEqualTo(TaskStatus.CI_POLLING);
    assertThat(after.mrUrl()).isEqualTo("http://host/1");
  }

  @Test
  void answersTrueAndTheStatusItFoundAndLeftWhenTheReportWasWritten() {
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          return write.apply(
                  reducer, TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
              != null;
        });

    boolean accepted = reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed");
    var landed =
        reports.report(
            "ABC-1",
            TaskStatus.REVIEW_PENDING,
            AgentReport.PLAIN,
            "widget fixed",
            (was, task) -> task);

    assertThat(accepted).isTrue();
    assertThat(landed)
        .contains(new FlowReports.Landed(TaskStatus.IN_PROGRESS, TaskStatus.REVIEW_PENDING));
  }

  @Test
  void answersFalseWhenTheTaskWasNotThereToWriteTheReportOn() {
    when(stateService.reduce(eq("ABC-1"), any())).thenReturn(false);

    assertThat(reports.report("ABC-1", TaskStatus.REVIEW_PENDING, "widget fixed")).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"PLAIN, widget fixed, VERIFYING", "QUESTION, which widget?, REVIEW_PENDING"})
  void holdsHandBackForTheOwedVerificationUnlessTheSessionAsks(
      AgentReport report, String message, TaskStatus lands) {
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          return write.apply(
                  reducer, TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build())
              != null;
        });

    var landed =
        reports.report(
            "ABC-1", TaskStatus.REVIEW_PENDING, report, message, (was, task) -> task, task -> true);

    assertThat(landed).contains(new FlowReports.Landed(TaskStatus.IN_PROGRESS, lands));
  }

  @Test
  void tellsTheSessionWhyItsReportWasRefusedInTheStatusItIsIn() {
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          return write.apply(
                  reducer, TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build())
              != null;
        });

    assertThatThrownBy(() -> reports.report("ABC-1", TaskStatus.PLAN_PENDING, "a plan"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("already REVIEW_PENDING");
  }

  @ParameterizedTest
  @EnumSource(
      value = AgentReport.class,
      names = {"QUESTION", "PERMISSION"})
  void refusesQuestionThatParksAtCiPolling(AgentReport asked) {
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          return write.apply(
                  reducer, TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
              != null;
        });

    assertThatThrownBy(() -> reports.report("ABC-1", TaskStatus.CI_POLLING, asked, "which one?"))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("report REVIEW_PENDING with your question");
  }

  @Test
  void landsQuestionReportedAtReviewPendingFromCiPolling() {
    AtomicReference<TaskState> written = new AtomicReference<>();
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          written.set(
              write.apply(
                  reducer, TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build()));
          return true;
        });

    reports.report("ABC-1", TaskStatus.REVIEW_PENDING, AgentReport.QUESTION, "which one?");

    assertThat(written.get().standing().status()).isEqualTo(TaskStatus.REVIEW_PENDING);
  }

  @Test
  void answersWhatRoundReadFoundAndLeftWhenItMovedTheTask() {
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          return write.apply(
                  reducer, TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build())
              != null;
        });

    var landed = reports.read("ABC-1", TaskStatus.APPROVED, "approved");

    assertThat(landed).contains(new FlowReports.Landed(TaskStatus.CI_POLLING, TaskStatus.APPROVED));
  }

  @Test
  void writesNothingWhenRoundReadFindsTheTaskWhereItAlreadyIs() {
    AtomicReference<TaskState> written = new AtomicReference<>();
    TaskState before = TaskState.builder("proj", "/wt", TaskStatus.APPROVED).build();
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          written.set(write.apply(reducer, before));
          return true;
        });

    reports.read("ABC-1", TaskStatus.APPROVED, "approved");

    assertThat(written.get()).isSameAs(before);
  }

  @ParameterizedTest
  @EnumSource(
      value = AgentReport.class,
      names = {"QUESTION", "PERMISSION"})
  void keepsTheQuestionOfSessionWhenRoundReadConcludesGreen(AgentReport asked) {
    AtomicReference<TaskState> written = new AtomicReference<>();
    TaskState before =
        TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
            .message("squash or keep the commits?")
            .report(asked)
            .build();
    when(stateService.reduce(eq("ABC-1"), any()))
        .thenAnswer(call -> {
          BiFunction<TaskReducer, TaskState, TaskState> write = call.getArgument(1);
          written.set(write.apply(reducer, before));
          return true;
        });

    reports.read("ABC-1", TaskStatus.REVIEWED, "reviewed");

    assertThat(written.get().standing().status()).isEqualTo(TaskStatus.REVIEWED);
    assertThat(written.get().standing().report()).isEqualTo(asked);
    assertThat(written.get().standing().message()).isEqualTo("squash or keep the commits?");
  }

  @Test
  void handsDeployedTaskBackWhenItsDeployPipelineIsRedOnTheCode() {
    reports.readDeploy("ABC-1", "web", "deploy pipeline red");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState handed =
        write
            .getValue()
            .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    assertThat(handed.standing().status()).isEqualTo(TaskStatus.DEPLOY_CONFLICT);
    assertThat(handed.standing().message()).isEqualTo("deploy pipeline red");
  }

  @Test
  void recordsTheRedPipelineAsTheCauseTheDeployWaitsOn() {
    reports.readDeploy("ABC-1", "web", "deploy pipeline red");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    Deployment recorded =
        Deployment.defaults()
            .withTargets(java.util.List.of(Deployment.Target.of("web", "dev").withPushedAt(5)));
    TaskState handed =
        write
            .getValue()
            .apply(
                reducer,
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).deployment(recorded).build());
    assertThat(
            Objects.requireNonNull(
                    Objects.requireNonNull(handed.work().deployment())
                        .conflictedTarget()
                        .get()
                        .progress()
                        .conflict())
                .cause())
        .isEqualTo(Deployment.Cause.RED_PIPELINE);
  }

  @Test
  void endsConflictHeldByRedPipelineAlone() {
    reports.endRedConflict("ABC-1", "deployed to dev");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    Deployment held =
        Deployment.defaults()
            .withTargets(
                java.util.List.of(
                    mergedWeb.withConflict(Conflict.of(Deployment.Cause.RED_PIPELINE, 1))));
    TaskState ended =
        write
            .getValue()
            .apply(
                reducer,
                TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT)
                    .deployment(held)
                    .build());
    assertThat(ended.standing().status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(ended.standing().message()).isEqualTo("deployed to dev");
  }

  @Test
  void leavesConflictOfMergeStandingWhateverTheDeployBranchPipelinesSay() {
    reports.endRedConflict("ABC-1", "deployed to dev");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    Deployment held =
        Deployment.defaults()
            .withTargets(
                java.util.List.of(mergedWeb.withConflict(Conflict.of(Deployment.Cause.MERGE, 1))));
    TaskState conflicted =
        TaskState.builder("proj", "/wt", TaskStatus.DEPLOY_CONFLICT).deployment(held).build();
    assertThat(write.getValue().apply(reducer, conflicted)).isSameAs(conflicted);
  }

  @Test
  void keepsDeployedTaskDeployedWhileItsPipelineIsOnlyWaited() {
    reports.readDeploy("ABC-1", null, "waiting on its pipeline");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState said =
        write
            .getValue()
            .apply(reducer, TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).build());
    assertThat(said.standing().status()).isEqualTo(TaskStatus.DEPLOYED);
    assertThat(said.standing().message()).isEqualTo("waiting on its pipeline");
  }

  @Test
  void movesNoTaskThatIsNotLiveOnTheDeployBranchWhateverItsDeployPipelineSays() {
    reports.readDeploy("ABC-1", "web", "deploy pipeline red");

    verify(stateService).reduce(eq("ABC-1"), write.capture());
    TaskState reverted = TaskState.builder("proj", "/wt", TaskStatus.REVERTED).build();
    assertThat(write.getValue().apply(reducer, reverted)).isSameAs(reverted);
  }

  @ParameterizedTest
  @CsvSource({
    "CI_POLLING,true,true",
    "CI_POLLING,false,false",
    "REVIEW_PENDING,true,false",
  })
  void missesTheShipOnlyWhenCiPollingLinksNothingButForeignRequests(
      TaskStatus said, boolean everyLinkForeign, boolean missed) {
    assertThat(FlowReports.missedShip(said, everyLinkForeign)).isEqualTo(missed);
  }

  @Test
  void leavesTheTaskWhereItStandsWhenTheShipIsMissed() {
    assertThat(FlowReports.landing(TaskStatus.CI_POLLING, TaskStatus.IN_PROGRESS, true))
        .isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void landsOnTheReportedStatusWhenAnyLinkIsOwned() {
    assertThat(FlowReports.landing(TaskStatus.CI_POLLING, TaskStatus.IN_PROGRESS, false))
        .isEqualTo(TaskStatus.CI_POLLING);
  }
}

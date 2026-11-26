package dev.jagt.orchestrator.service.report;

import static dev.jagt.orchestrator.service.report.TestAgentStatusReportsBuilder.agentStatusReports;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TestStateServiceBuilder;
import dev.jagt.orchestrator.service.session.LinkRefusals;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.Holds;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentStatusReportsPlanTest {

  private final LinkRefusals refusals = mock(LinkRefusals.class);
  private final WorktreeChanges worktreeChanges = mock(WorktreeChanges.class);
  private final ConfigService configService = mock(ConfigService.class);

  private final TaskState inProgress =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build();

  private final ConfigFile projOnly =
      ConfigFile.defaults()
          .withProjects(
              Map.of(
                  "proj",
                  new dev.jagt.orchestrator.task.repo.ProjectConfig(
                      "/repo", "origin/main", "dev", List.of())));

  private final ConfigFile withRegistry =
      projOnly.withProjects(
          Map.of(
              "proj",
              new dev.jagt.orchestrator.task.repo.ProjectConfig(
                  "/repo", "origin/main", "dev", List.of()),
              "registry",
              new dev.jagt.orchestrator.task.repo.ProjectConfig(
                  "/reg", "origin/main", "dev", List.of())));

  @TempDir Path root;

  private StateService state;
  private AgentStatusReports reports;
  private TaskState planning;
  private ThrowingCallable planReport;

  @BeforeEach
  void wireReports() {
    state = TestStateServiceBuilder.stateService().withRoot(root).build();
    reports = agentStatusReports().build(state, refusals, worktreeChanges, configService);
    planning = TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build();
    planReport = () -> reports.report(TaskStatus.PLAN_PENDING, "plan ready", "ABC-1");
  }

  @Test
  void refusesPlanWithoutAcceptanceCriteria() throws IOException {
    Files.writeString(root.resolve("plan.md"), "# Plan\n1. change the guide\n");
    state.putTask("ABC-1", planning);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(planReport)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("## Acceptance criteria");
    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void recordsTheProbesPlanDeclaresAsItIsPutUp() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        """
        # Plan
        ## Acceptance criteria
        - [here] guide exists
        ```probes
        artifact docs/guide.md
        ```
        """);
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.PLAN_PENDING, "plan ready", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().gate().probes())
        .containsExactly(Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/guide.md"), 0));
  }

  @Test
  void refusesPlanWhoseProbesBlockHoldsLineNoProbeReads() throws IOException {
    Files.writeString(
        root.resolve("plan.md"),
        """
        ## Acceptance criteria
        - [here] builds
        ```probes
        command ./gradlew test | tee out.log
        ```
        """);
    state.putTask("ABC-1", planning);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(planReport)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("| tee out.log");
    assertThat(state.task("ABC-1").orElseThrow().standing().status())
        .isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void opensThePlanGateWhenAnAutoplanSessionReportsThatTheTaskNeedsNoPlan() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW).planGate(PlanGate.CLOSED).build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS", "one file, no contradiction", "no_plan", null, Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().gate())
        .returns(PlanGate.OPEN, Holds::planGate)
        .returns(Map.of(), Holds::refusals);
  }

  @Test
  void keepsThePlanGateWhenSessionOpenedInPlanModeReportsThatTheTaskNeedsNoPlan() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW).planGate(PlanGate.REQUIRED).build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(() ->
            reports.report(
                new AgentStatusMessage("IN_PROGRESS", "one file", "no_plan", null, Map.of()),
                "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outcome");
    assertThat(state.task("ABC-1").orElseThrow().gate().planGate()).isEqualTo(PlanGate.REQUIRED);
  }

  @Test
  void keepsThePlanGateClosedWhereTheTrackerOpenedTheTaskAndItsSessionReportsNoPlan() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.NEW)
            .planGate(PlanGate.CLOSED)
            .openedBy(ActionOrigin.TRACKER)
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThatThrownBy(() ->
            reports.report(
                new AgentStatusMessage("IN_PROGRESS", "one file", "no_plan", null, Map.of()),
                "ABC-1"))
        .isInstanceOf(Refusal.class)
        .extracting(refusal -> ((Refusal) refusal).code())
        .isEqualTo(Refusal.Code.STATE);
    assertThat(state.task("ABC-1").orElseThrow())
        .returns(PlanGate.CLOSED, task -> task.gate().planGate())
        .returns(TaskStatus.NEW, task -> task.standing().status())
        .returns(Map.of(RefusalKind.STATUS_REPORT, 1), task -> task.gate().refusals());
  }

  @Test
  void recordsTheProjectQuestionNeedsOnTheTask() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(withRegistry);

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS",
            "needs the registry",
            "question",
            new AgentStatusMessage.Request(null, Map.of()),
            null,
            "registry",
            null,
            Map.of()),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().work().repoRequest()).isEqualTo("registry");
  }

  @Test
  void refusesProjectNoConfigurationHasAndRecordsNothing() {
    state.putTask("ABC-1", inProgress);
    when(configService.load()).thenReturn(projOnly);

    assertThatThrownBy(() ->
            reports.report(
                new AgentStatusMessage(
                    "IN_PROGRESS",
                    "needs it",
                    "question",
                    new AgentStatusMessage.Request(null, Map.of()),
                    null,
                    "ghost",
                    null,
                    Map.of()),
                "ABC-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needsProject")
        .hasMessageContaining("[proj]");
    assertThat(state.task("ABC-1").orElseThrow().work().repoRequest()).isNull();
  }

  @Test
  void dropsTheRequestWhenTheSessionReportsAnythingElse() {
    state.putTask(
        "ABC-1",
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .alias("a1")
            .repoRequest("registry")
            .build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(TaskStatus.IN_PROGRESS, "back to work", "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().work().repoRequest()).isNull();
  }

  @Test
  void owesJudgementOnEverySwitchThatIsOnToSessionStillInNew() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(
                    ConfigFile.AgentConfig.defaults()
                        .withVisualize(new ConfigFile.VisualizeConfig(true))));

    MessageContext context = reports.sessionContextFor("ABC-1");
    assertThat(context.switchesOn()).containsExactly("visualize");
    assertThat(context.firstReport()).isTrue();
  }

  @Test
  void knowsNoSwitchIsOnWhenNoneIs() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThat(reports.sessionContextFor("ABC-1").switchesOn()).isEmpty();
  }

  @Test
  void marksTheFirstReportOnlyWhileTheTaskIsNew() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withAgent(
                    ConfigFile.AgentConfig.defaults()
                        .withVisualize(new ConfigFile.VisualizeConfig(true))));

    assertThat(reports.sessionContextFor("ABC-1").firstReport()).isFalse();
  }

  @Test
  void missesThePictureWhileTheWorktreeHoldsNoTaskVisual() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThat(reports.sessionContextFor("ABC-1").visualMissing()).isTrue();
  }

  @Test
  void missesNoPictureOnceTheWorktreeHoldsTheTaskVisual() throws IOException {
    Files.writeString(root.resolve("task_visual.md"), "picture");
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThat(reports.sessionContextFor("ABC-1").visualMissing()).isFalse();
  }

  @Test
  void missesNoPictureOfTaskWithoutWorktree() {
    state.putTask("ABC-1", TaskState.builder("proj", "", TaskStatus.IN_PROGRESS).build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    assertThat(reports.sessionContextFor("ABC-1").visualMissing()).isFalse();
  }

  @Test
  void recordsTheJudgementsReportCarriesOnTheTask() {
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.NEW).build());
    when(configService.load()).thenReturn(ConfigFile.defaults());

    reports.report(
        new AgentStatusMessage(
            "IN_PROGRESS",
            "working",
            null,
            new AgentStatusMessage.Request(null, Map.of()),
            null,
            null,
            null,
            Map.of("visualize", "no — config-only change")),
        "ABC-1");

    assertThat(state.task("ABC-1").orElseThrow().gate().switches())
        .containsExactly(Map.entry("visualize", "no — config-only change"));
  }
}

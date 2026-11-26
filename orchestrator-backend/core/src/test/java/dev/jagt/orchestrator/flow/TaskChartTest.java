package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.deploy.Conflict;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.statechart.Macrostep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskChartTest {

  private final TaskChart taskChart = new TaskChart(Composition.builtIn());

  @Test
  void startsAtNewOnChartTheEngineValidatedAsReachableAndLeavable() {
    assertThat(taskChart.chart().initial().toPlain())
        .containsExactly(
            entry("lifecycle", "NEW"),
            entry("stop", "NONE"),
            entry("plan", "OPEN"),
            entry("report", "PLAIN"),
            entry("turn", "CLOSED"),
            entry("deploy", "NONE"),
            entry("git", "UNREAD"),
            entry("repo", "NONE"),
            entry("request", "NONE"),
            entry("checks", "NONE"),
            entry("approval", "UNREAD"),
            entry("infra", "NONE"),
            entry("acceptance", "NONE"),
            entry("master", "STANDS_IN"),
            entry("session", "NONE"));
  }

  @Test
  void rendersTheDiagramTheDocsPageShows() throws IOException {
    String[] regions = taskChart.chart().render().split("    --\n", 0);

    assertThat(Files.readString(Path.of("../../docs/task-chart.md")))
        .isEqualTo("# The task lifecycle chart\n\n```mermaid\n" + regions[0] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-stop-chart.md")))
        .isEqualTo("# The task stop chart\n\n```mermaid\nstateDiagram-v2\n" + regions[1] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-plan-chart.md")))
        .isEqualTo("# The task plan chart\n\n```mermaid\nstateDiagram-v2\n" + regions[2] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-report-chart.md")))
        .isEqualTo(
            "# The task report chart\n\n```mermaid\nstateDiagram-v2\n" + regions[3] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-turn-chart.md")))
        .isEqualTo("# The task turn chart\n\n```mermaid\nstateDiagram-v2\n" + regions[4] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-deploy-chart.md")))
        .isEqualTo(
            "# The task deploy chart\n\n```mermaid\nstateDiagram-v2\n" + regions[5] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-session-chart.md")))
        .isEqualTo(
            "# The task session chart\n\n```mermaid\nstateDiagram-v2\n" + regions[14] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-git-chart.md")))
        .isEqualTo("# The task git chart\n\n```mermaid\nstateDiagram-v2\n" + regions[6] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-repo-chart.md")))
        .isEqualTo("# The task repo chart\n\n```mermaid\nstateDiagram-v2\n" + regions[7] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-request-chart.md")))
        .isEqualTo(
            "# The task request chart\n\n```mermaid\nstateDiagram-v2\n" + regions[8] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-checks-chart.md")))
        .isEqualTo(
            "# The task checks chart\n\n```mermaid\nstateDiagram-v2\n" + regions[9] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-approval-chart.md")))
        .isEqualTo(
            "# The task approval chart\n\n```mermaid\nstateDiagram-v2\n" + regions[10] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-master-chart.md")))
        .isEqualTo(
            "# The task master chart\n\n```mermaid\nstateDiagram-v2\n" + regions[13] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-acceptance-chart.md")))
        .isEqualTo(
            "# The task acceptance chart\n\n```mermaid\nstateDiagram-v2\n" + regions[12] + "```\n");
    assertThat(Files.readString(Path.of("../../docs/task-infra-chart.md")))
        .isEqualTo(
            "# The task infra chart\n\n```mermaid\nstateDiagram-v2\n" + regions[11] + "```\n");
  }

  static Stream<Arguments> deployEdges() {
    DeployStep.Opened opened = new DeployStep.Opened(List.of(Deployment.Target.of("api", "dev")));
    DeployStep.Conflicted conflicted = new DeployStep.Conflicted("api", Deployment.Cause.MERGE);
    DeployStep.Seen seen = new DeployStep.Seen(new Conflict.Progress(1, 0, "d"));
    return Stream.of(
        arguments("NONE", opened, "LANDING", List.of(new TaskEffect.Deploys(opened))),
        arguments("CHECK_ASKED", opened, "LANDING", List.of(new TaskEffect.Deploys(opened))),
        arguments(
            "LANDING",
            new DeployStep.Merged("api", "a1"),
            "LANDING",
            List.of(new TaskEffect.Deploys(new DeployStep.Merged("api", "a1")))),
        arguments(
            "HANDED",
            new DeployStep.Fixed("api", "a1"),
            "LANDING",
            List.of(new TaskEffect.Deploys(new DeployStep.Fixed("api", "a1")))),
        arguments(
            "LANDING",
            new DeployStep.Pushed("api", 5),
            "LANDING",
            List.of(new TaskEffect.Deploys(new DeployStep.Pushed("api", 5)))),
        arguments(
            "HANDED",
            new DeployStep.Blocked("api"),
            "LANDING",
            List.of(new TaskEffect.Deploys(new DeployStep.Blocked("api")))),
        arguments("NONE", new DeployStep.Merged("api", "a1"), "NONE", List.of()),
        arguments("REVERTING", new DeployStep.Pushed("api", 5), "REVERTING", List.of()),
        arguments("CHECK_ASKED", new DeployStep.Blocked("api"), "CHECK_ASKED", List.of()),
        arguments("NONE", conflicted, "HANDED", List.of(new TaskEffect.Deploys(conflicted))),
        arguments("REVERTING", conflicted, "HANDED", List.of(new TaskEffect.Deploys(conflicted))),
        arguments(
            "LANDING",
            new DeployStep.Reverted("api", "b2"),
            "REVERTING",
            List.of(new TaskEffect.Deploys(new DeployStep.Reverted("api", "b2")))),
        arguments(
            "HANDED",
            new DeployStep.Reverted("api", "b2"),
            "REVERTING",
            List.of(new TaskEffect.Deploys(new DeployStep.Reverted("api", "b2")))),
        arguments(
            "HANDED",
            new DeployStep.Discarded("api"),
            "LANDING",
            List.of(new TaskEffect.Deploys(new DeployStep.Discarded("api")))),
        arguments("LANDING", new DeployStep.Discarded("api"), "LANDING", List.of()),
        arguments("HANDED", seen, "HANDED", List.of(new TaskEffect.Deploys(seen))),
        arguments(
            "HANDED",
            new DeployStep.Told("red"),
            "HANDED",
            List.of(new TaskEffect.Deploys(new DeployStep.Told("red")))),
        arguments(
            "HANDED",
            new DeployStep.Held(9),
            "HANDED",
            List.of(new TaskEffect.Deploys(new DeployStep.Held(9)))),
        arguments("LANDING", seen, "LANDING", List.of()),
        arguments(
            "LANDING",
            new DeployStep.Asked("dg"),
            "CHECK_ASKED",
            List.of(new TaskEffect.Deploys(new DeployStep.Asked("dg")))),
        arguments("HANDED", new DeployStep.Asked("dg"), "HANDED", List.of()),
        arguments(
            "CHECK_ASKED",
            new DeployStep.Bare(List.of("api")),
            "CHECK_ASKED",
            List.of(new TaskEffect.Deploys(new DeployStep.Bare(List.of("api"))))),
        arguments(
            "LANDING",
            new DeployStep.ReadOn("x"),
            "LANDING",
            List.of(new TaskEffect.Deploys(new DeployStep.ReadOn("x")))),
        arguments("HANDED", new DeployStep.ReadOn("x"), "HANDED", List.of()));
  }

  @ParameterizedTest
  @MethodSource("deployEdges")
  void movesTheDeployRegionOnlyAlongTheStepsItsPhaseTakes(
      String from, DeployStep step, String to, List<TaskEffect> effects) {
    Map<String, String> plain = new HashMap<>(taskChart.chart().initial().toPlain());
    plain.put("deploy", from);

    Macrostep<TaskEffect> stepped =
        taskChart
            .chart()
            .step(
                taskChart.chart().configuration(plain),
                new ChartFacts(false, Map.of(), Facts.defaults(), ChartFacts.PROJECTED),
                new TaskMove.DeployStepped(step));

    assertThat(stepped.configuration().toPlain()).containsEntry("deploy", to);
    assertThat(stepped.effects()).isEqualTo(effects);
  }

  static Stream<Arguments> infraEdges() {
    InfraRetry first = new InfraRetry(1, "shop", "p", "j", "r", false, 1, 3);
    InfraRetry last = new InfraRetry(1, "shop", "p", "j", "r", true, 3, 3);
    return Stream.of(
        arguments(
            "SENT",
            new TaskMove.RequestLinked(Map.of(), "u", true, false, false),
            "NONE",
            List.of(new TaskEffect.ResetsInfra())),
        arguments(
            "SPENT",
            new TaskMove.RequestLinked(Map.of(), "u", false, true, false),
            "NONE",
            List.of(new TaskEffect.ResetsInfra())),
        arguments(
            "NONE",
            new TaskMove.InfraArmed(first),
            "ARMED",
            List.of(new TaskEffect.ArmsInfra(first))),
        arguments(
            "SENT",
            new TaskMove.InfraCleared(true, InfraPhase.NONE),
            "NONE",
            List.of(new TaskEffect.ClearsInfra(true))),
        arguments(
            "SENT",
            new TaskMove.InfraCleared(false, InfraPhase.SENT),
            "SENT",
            List.of(new TaskEffect.ClearsInfra(false))),
        arguments(
            "ARMED",
            new TaskMove.InfraCleared(false, InfraPhase.SPENT),
            "SPENT",
            List.of(new TaskEffect.ClearsInfra(false))),
        arguments(
            "ARMED",
            new TaskMove.InfraCleared(false, InfraPhase.NONE),
            "NONE",
            List.of(new TaskEffect.ClearsInfra(false))),
        arguments(
            "ARMED",
            new TaskMove.InfraCleared(false, InfraPhase.SENT),
            "SENT",
            List.of(new TaskEffect.ClearsInfra(false))),
        arguments(
            "NONE",
            new TaskMove.InfraSent(first),
            "SENT",
            List.of(new TaskEffect.SendsInfra(first))),
        arguments(
            "ARMED",
            new TaskMove.InfraSent(last),
            "SPENT",
            List.of(new TaskEffect.SendsInfra(last))),
        arguments(
            "ARMED",
            new TaskMove.HeadSeen("t", true, false, InfraPhase.SENT),
            "SENT",
            List.of(new TaskEffect.DisarmsInfra())),
        arguments(
            "ARMED",
            new TaskMove.HeadSeen("t", true, false, InfraPhase.SPENT),
            "SPENT",
            List.of(new TaskEffect.DisarmsInfra())),
        arguments(
            "ARMED",
            new TaskMove.HeadSeen("t", true, false, InfraPhase.NONE),
            "NONE",
            List.of(new TaskEffect.DisarmsInfra())),
        arguments(
            "ARMED",
            new TaskMove.HeadSeen("t", false, false, InfraPhase.SENT),
            "ARMED",
            List.of()));
  }

  @ParameterizedTest
  @MethodSource("infraEdges")
  void movesTheInfraRegionOnlyAlongTheMovesItsPhaseTakes(
      String from, TaskMove move, String to, List<TaskEffect> effects) {
    Map<String, String> plain = new HashMap<>(taskChart.chart().initial().toPlain());
    plain.put("infra", from);

    Macrostep<TaskEffect> stepped =
        taskChart
            .chart()
            .step(
                taskChart.chart().configuration(plain),
                new ChartFacts(false, Map.of(), Facts.defaults(), ChartFacts.PROJECTED),
                move);

    assertThat(stepped.configuration().toPlain()).containsEntry("infra", to);
    assertThat(stepped.effects()).containsAll(effects);
  }

  static Stream<Arguments> sessionEdges() {
    SessionSign.Read running = new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 0, null);
    SessionSign.Read dead = new SessionSign.Read(Session.Window.State.DEAD_SHELL, 0, 0, 0, 0, null);
    return Stream.of(
        arguments("NONE", new SessionSign.WorkStarted(), "WORKING"),
        arguments("QUIET", new SessionSign.Called(), "WORKING"),
        arguments("IDLE", new SessionSign.ToolUsed(), "WORKING"),
        arguments("IDLE", new SessionSign.Reported(), "IDLE"),
        arguments("QUIET", new SessionSign.LogNamed("l"), "QUIET"),
        arguments("WORKING", running, "QUIET"),
        arguments("IDLE", running, "QUIET"),
        arguments("FAILED", running, "FAILED"),
        arguments("GONE", running, "GONE"),
        arguments("WORKING", dead, "GONE"),
        arguments("WORKING", new SessionSign.Ended("e"), "GONE"),
        arguments("GONE", new SessionSign.Restarted("r"), "IDLE"),
        arguments("WORKING", new SessionSign.TurnEnded("l"), "IDLE"),
        arguments("WORKING", new SessionSign.TurnFailed("c"), "FAILED"),
        arguments("FAILED", new SessionSign.TurnFailed("c"), "FAILED"));
  }

  @ParameterizedTest
  @MethodSource("sessionEdges")
  void movesTheSessionRegionOnlyAlongTheSignsItsPhaseTakes(
      String from, SessionSign sign, String to) {
    Map<String, String> plain = new HashMap<>(taskChart.chart().initial().toPlain());
    plain.put("session", from);

    Macrostep<TaskEffect> stepped =
        taskChart
            .chart()
            .step(
                taskChart.chart().configuration(plain),
                new ChartFacts(false, Map.of(), Facts.defaults(), ChartFacts.PROJECTED),
                new TaskMove.Signed(sign));

    assertThat(stepped.configuration().toPlain()).containsEntry("session", to);
    assertThat(stepped.effects()).contains(new TaskEffect.Signs(sign));
  }

  static Stream<Arguments> landings() {
    return Stream.of(
        arguments(
            TaskStatus.IN_PROGRESS,
            new TaskMove.Ran(TaskAction.SHIP, Outcome.Kind.OK),
            TaskStatus.CI_POLLING),
        arguments(
            TaskStatus.IN_PROGRESS,
            new TaskMove.Ran(TaskAction.SHIP, Outcome.Kind.RELAYED),
            TaskStatus.SHIPPING),
        arguments(
            TaskStatus.PLAN_PENDING,
            new TaskMove.Ran(TaskAction.APPROVE_PLAN, Outcome.Kind.RELAYED),
            TaskStatus.IN_PROGRESS),
        arguments(
            TaskStatus.REVIEWED,
            new TaskMove.Ran(TaskAction.ADD_REPO, Outcome.Kind.RELAYED),
            TaskStatus.IN_PROGRESS),
        arguments(
            TaskStatus.REVIEWED,
            new TaskMove.Ran(TaskAction.DEPLOY, Outcome.Kind.OK),
            TaskStatus.DEPLOYED),
        arguments(
            TaskStatus.REVIEWED,
            new TaskMove.Ran(TaskAction.DEPLOY, Outcome.Kind.CONFLICT),
            TaskStatus.DEPLOY_CONFLICT),
        arguments(TaskStatus.DEPLOY_CONFLICT, new TaskMove.DeployRead(false), TaskStatus.DEPLOYED),
        arguments(
            TaskStatus.DEPLOYED,
            new TaskMove.Ran(TaskAction.REVERT, Outcome.Kind.OK),
            TaskStatus.REVERTED),
        arguments(
            TaskStatus.DEPLOYED,
            new TaskMove.Ran(TaskAction.REVERT, Outcome.Kind.CONFLICT),
            TaskStatus.DEPLOY_CONFLICT),
        arguments(
            TaskStatus.REVERTED,
            new TaskMove.Ran(TaskAction.REVERT, Outcome.Kind.PARTIAL),
            TaskStatus.DEPLOYED),
        arguments(
            TaskStatus.REVIEWED,
            new TaskMove.Ran(TaskAction.DONE, Outcome.Kind.GONE),
            TaskStatus.DONE),
        arguments(
            TaskStatus.CI_POLLING, new TaskMove.Read(TaskStatus.REVIEWED), TaskStatus.REVIEWED),
        arguments(TaskStatus.REVIEWED, new TaskMove.Read(TaskStatus.APPROVED), TaskStatus.APPROVED),
        arguments(
            TaskStatus.APPROVED, new TaskMove.Read(TaskStatus.CI_FAILED), TaskStatus.CI_FAILED),
        arguments(
            TaskStatus.IN_PROGRESS,
            new TaskMove.Reported(TaskStatus.REVIEW_PENDING, false),
            TaskStatus.REVIEW_PENDING),
        arguments(
            TaskStatus.IN_PROGRESS,
            new TaskMove.Reported(TaskStatus.REVIEW_PENDING, true),
            TaskStatus.VERIFYING),
        arguments(
            TaskStatus.DEPLOY_CONFLICT,
            new TaskMove.Reported(TaskStatus.SHIPPING, false),
            TaskStatus.DEPLOY_CONFLICT),
        arguments(
            TaskStatus.NEW,
            new TaskMove.Reported(TaskStatus.PLAN_PENDING, false),
            TaskStatus.PLAN_PENDING),
        arguments(
            TaskStatus.CI_FAILED,
            new TaskMove.Reported(TaskStatus.CI_POLLING, false),
            TaskStatus.CI_POLLING));
  }

  @ParameterizedTest
  @MethodSource("landings")
  void landsTaskOnlyWhereTheChartDrawsTheMoveFromItsStatus(
      TaskStatus from, TaskMove move, TaskStatus to) {
    assertThat(taskChart.land(from, move)).contains(to);
  }

  @Test
  void taskReachesDoneByTheHumansVerbsAloneWhenNoRoleIsComposed() {
    TaskChart noRole = new TaskChart(new Composition(List.of()));
    Facts facts =
        Facts.defaults()
            .withDelivery(
                Facts.Delivery.defaults()
                    .withHasReviewRequest(true)
                    .withLiveDeploy(true)
                    .withChecksGreen(true));
    FlowRules rules = new FlowRules(noRole);

    assertThat(rules.allows(TaskStatus.IN_PROGRESS, TaskAction.SHIP, facts)).isTrue();
    assertThat(
            noRole.land(TaskStatus.IN_PROGRESS, new TaskMove.Ran(TaskAction.SHIP, Outcome.Kind.OK)))
        .contains(TaskStatus.CI_POLLING);
    assertThat(noRole.land(TaskStatus.CI_POLLING, new TaskMove.Read(TaskStatus.REVIEWED)))
        .contains(TaskStatus.REVIEWED);
    assertThat(rules.allows(TaskStatus.REVIEWED, TaskAction.DEPLOY, facts)).isTrue();
    assertThat(
            noRole.land(TaskStatus.REVIEWED, new TaskMove.Ran(TaskAction.DEPLOY, Outcome.Kind.OK)))
        .contains(TaskStatus.DEPLOYED);
    assertThat(rules.allows(TaskStatus.DEPLOYED, TaskAction.DONE, facts)).isTrue();
    assertThat(
            noRole.land(TaskStatus.DEPLOYED, new TaskMove.Ran(TaskAction.DONE, Outcome.Kind.GONE)))
        .contains(TaskStatus.DONE);
  }

  static Stream<Arguments> acceptanceEdges() {
    return Stream.of(
        arguments(
            "DEPLOYED",
            "NONE",
            new TaskMove.Relayed("accept", "d", "a"),
            CheckerPhase.NONE,
            "READING",
            new TaskEffect.OpensCheckerRead("acceptance", "accept")),
        arguments(
            "IN_PROGRESS",
            "NONE",
            new TaskMove.Relayed("accept", "d", "a"),
            CheckerPhase.NONE,
            "NONE",
            new TaskEffect.OpensTurn("accept", "d", "a")),
        arguments(
            "DEPLOYED",
            "READING",
            new TaskMove.TurnClosed(),
            CheckerPhase.ROUND_OPEN,
            "ROUND_OPEN",
            new TaskEffect.ClosesCheckerRead("acceptance")),
        arguments(
            "DEPLOYED",
            "READING",
            new TaskMove.TurnClosed(),
            CheckerPhase.PASSED,
            "PASSED",
            new TaskEffect.ClosesCheckerRead("acceptance")),
        arguments(
            "DEPLOYED",
            "READING",
            new TaskMove.TurnClosed(),
            CheckerPhase.NONE,
            "NONE",
            new TaskEffect.ClosesCheckerRead("acceptance")),
        arguments(
            "DEPLOYED",
            "READING",
            new TaskMove.Checked.ReadEnded("acceptance", true),
            CheckerPhase.NONE,
            "NONE",
            new TaskEffect.ClosesCheckerRead("acceptance")),
        arguments(
            "DEPLOYED",
            "READING",
            new TaskMove.Checked.ReadEnded("acceptance", true),
            CheckerPhase.NONE,
            "NONE",
            new TaskEffect.ClosesTurn()),
        arguments(
            "DEPLOYED",
            "READING",
            new TaskMove.Ran(TaskAction.REVERT, Outcome.Kind.OK),
            CheckerPhase.PASSED,
            "PASSED",
            new TaskEffect.ClosesCheckerRead("acceptance")),
        arguments(
            "DEPLOYED",
            "NONE",
            new TaskMove.Checked.Passed("acceptance", "s1", true),
            CheckerPhase.NONE,
            "PASSED",
            new TaskEffect.PassesChecker("acceptance", "s1")),
        arguments(
            "DEPLOYED",
            "READING",
            new TaskMove.Checked.Passed("acceptance", "s1", true),
            CheckerPhase.NONE,
            "READING",
            new TaskEffect.PassesChecker("acceptance", "s1")),
        arguments(
            "DEPLOYED",
            "ROUND_OPEN",
            new TaskMove.Checked.Passed("acceptance", "s1", false),
            CheckerPhase.NONE,
            "ROUND_OPEN",
            new TaskEffect.PassesChecker("acceptance", "s1")),
        arguments(
            "DEPLOYED",
            "NONE",
            new TaskMove.Checked.RoundSent("acceptance", "d", "r", true),
            CheckerPhase.NONE,
            "ROUND_OPEN",
            new TaskEffect.SendsCheckerRound("acceptance", "d", "r")),
        arguments(
            "DEPLOYED",
            "READING",
            new TaskMove.Checked.RoundSent("acceptance", "d", "r", true),
            CheckerPhase.NONE,
            "READING",
            new TaskEffect.SendsCheckerRound("acceptance", "d", "r")),
        arguments(
            "DEPLOYED",
            "PASSED",
            new TaskMove.Checked.RoundSent("acceptance", "d", "r", false),
            CheckerPhase.NONE,
            "PASSED",
            new TaskEffect.SendsCheckerRound("acceptance", "d", "r")));
  }

  @ParameterizedTest
  @MethodSource("acceptanceEdges")
  void movesTheAcceptanceRegionOnlyAlongTheMovesItsPhaseTakes(
      String lifecycle,
      String from,
      TaskMove move,
      CheckerPhase beneath,
      String to,
      TaskEffect effect) {
    Map<String, String> plain = new HashMap<>(taskChart.chart().initial().toPlain());
    plain.put("lifecycle", lifecycle);
    plain.put("acceptance", from);

    Macrostep<TaskEffect> stepped =
        taskChart
            .chart()
            .step(
                taskChart.chart().configuration(plain),
                new ChartFacts(
                    false, Map.of("acceptance", beneath), Facts.defaults(), ChartFacts.PROJECTED),
                move);

    assertThat(stepped.configuration().toPlain()).containsEntry("acceptance", to);
    assertThat(stepped.effects()).contains(effect);
  }

  @Test
  void landsDeployedWhenTheSessionDeclinedTheOpenAcceptanceRoundWithNoChanges() {
    Map<String, String> plain = new HashMap<>(taskChart.chart().initial().toPlain());
    plain.put("lifecycle", "IN_PROGRESS");
    plain.put("turn", "OPEN");
    plain.put("acceptance", "ROUND_OPEN");

    Macrostep<TaskEffect> stepped =
        taskChart
            .chart()
            .step(
                taskChart.chart().configuration(plain),
                new ChartFacts(
                    false,
                    Map.of("acceptance", CheckerPhase.ROUND_OPEN),
                    Facts.defaults(),
                    ChartFacts.PROJECTED),
                new TaskMove.Checked.RoundDeclined(
                    "acceptance", AgentReport.NO_CHANGES, TaskStatus.IN_PROGRESS));

    assertThat(stepped.configuration().toPlain()).containsEntry("lifecycle", "DEPLOYED");
    assertThat(stepped.effects()).contains(new TaskEffect.DeclinesRound("acceptance"));
  }

  static Stream<Arguments> regionEdges() {
    TaskStop checksStop = new TaskStop(TaskStop.Trigger.CHECKS, 1, "e");
    Turn closed = Turn.none();
    TaskRepo merged = new TaskRepo("p", "/w", null, "u", true, null);
    return Stream.of(
        arguments(
            "report",
            "QUESTION",
            "IN_PROGRESS",
            new TaskMove.Verified(true),
            "PLAIN",
            List.of(new TaskEffect.Reports(AgentReport.PLAIN))),
        arguments(
            "report",
            "WAIT",
            "IN_PROGRESS",
            new TaskMove.Read(TaskStatus.IN_PROGRESS),
            "PLAIN",
            List.of(new TaskEffect.Reports(AgentReport.PLAIN))),
        arguments(
            "report",
            "QUESTION",
            "REVERTED",
            new TaskMove.Said(AgentReport.WAIT),
            "PLAIN",
            List.of(new TaskEffect.Reports(AgentReport.PLAIN))),
        arguments(
            "report",
            "QUESTION",
            "DEPLOYED",
            new TaskMove.DeployRead(true),
            "PLAIN",
            List.of(new TaskEffect.Reports(AgentReport.PLAIN))),
        arguments(
            "report",
            "QUESTION",
            "DEPLOYED",
            new TaskMove.DeployRead(false),
            "QUESTION",
            List.of()),
        arguments(
            "report", "QUESTION", "IN_PROGRESS", new TaskMove.RepoUnasked(), "QUESTION", List.of()),
        arguments("report", "WAIT", "IN_PROGRESS", new TaskMove.RepoUnasked(), "WAIT", List.of()),
        arguments(
            "report",
            "QUESTION",
            "IN_PROGRESS",
            new TaskMove.Said(AgentReport.WAIT),
            "WAIT",
            List.of(new TaskEffect.Reports(AgentReport.WAIT))),
        arguments(
            "report", "QUESTION", "REVERTED", new TaskMove.RepoUnasked(), "QUESTION", List.of()),
        arguments(
            "git",
            "UNREAD",
            "IN_PROGRESS",
            new TaskMove.GitTrusted(GitTrust.of(Map.of())),
            "TRUSTED",
            List.of(new TaskEffect.TrustsGit(GitTrust.of(Map.of())))),
        arguments(
            "git",
            "TRUSTED",
            "IN_PROGRESS",
            new TaskMove.GitRepoJoined(GitTrust.of(Map.of())),
            "TRUSTED",
            List.of(new TaskEffect.JoinsGit(GitTrust.of(Map.of())))),
        arguments(
            "repo",
            "NONE",
            "IN_PROGRESS",
            new TaskMove.RepoAsked("p"),
            "ASKED",
            List.of(new TaskEffect.AsksRepo("p"))),
        arguments(
            "checks",
            "NONE",
            "IN_PROGRESS",
            new TaskMove.ChecksUnread(new ChecksUnread(1L, "c")),
            "UNREAD",
            List.of(new TaskEffect.FailsToReadChecks(new ChecksUnread(1L, "c")))),
        arguments(
            "repo",
            "ASKED",
            "IN_PROGRESS",
            new TaskMove.RepoAsked("p"),
            "ASKED",
            List.of(new TaskEffect.AsksRepo("p"))),
        arguments(
            "request",
            "NONE",
            "IN_PROGRESS",
            new TaskMove.RequestLinked(Map.of(), "u", false, false, false),
            "NONE",
            List.of(new TaskEffect.LinksRequests(Map.of(), "u", false))),
        arguments(
            "request",
            "MERGED",
            "IN_PROGRESS",
            new TaskMove.RepoAttached(merged),
            "MERGED",
            List.of()),
        arguments(
            "checks",
            "NONE",
            "IN_PROGRESS",
            new TaskMove.ChecksRead("ok", "t", false),
            "STALE",
            List.of(new TaskEffect.ReadsChecks("ok", "t"))),
        arguments(
            "checks",
            "NONE",
            "IN_PROGRESS",
            new TaskMove.HeadSeen("t", false, false, InfraPhase.NONE),
            "NONE",
            List.of(new TaskEffect.SeesHead("t"))),
        arguments(
            "checks",
            "STALE",
            "IN_PROGRESS",
            new TaskMove.HeadSeen("t", false, false, InfraPhase.NONE),
            "READ",
            List.of(new TaskEffect.SeesHead("t"))),
        arguments(
            "approval",
            "UNREAD",
            "IN_PROGRESS",
            new TaskMove.ApprovalRead(false),
            "WITHHELD",
            List.of(new TaskEffect.ReadsApproval(false))),
        arguments(
            "stop",
            "NONE",
            "IN_PROGRESS",
            new TaskMove.StopSet(checksStop),
            "HOST_READ",
            List.of(new TaskEffect.Stops(checksStop))),
        arguments(
            "turn",
            "OPEN",
            "IN_PROGRESS",
            new TaskMove.TurnRestored(closed),
            "CLOSED",
            List.of(new TaskEffect.RestoresTurn(closed))));
  }

  @ParameterizedTest
  @MethodSource("regionEdges")
  void movesRegionOnlyAlongTheMovesItsPhaseTakes(
      String region,
      String from,
      String lifecycle,
      TaskMove move,
      String to,
      List<TaskEffect> effects) {
    Map<String, String> plain = new HashMap<>(taskChart.chart().initial().toPlain());
    plain.put("lifecycle", lifecycle);
    plain.put(region, from);

    Macrostep<TaskEffect> stepped =
        taskChart
            .chart()
            .step(
                taskChart.chart().configuration(plain),
                new ChartFacts(false, Map.of(), Facts.defaults(), ChartFacts.PROJECTED),
                move);

    assertThat(stepped.configuration().toPlain()).containsEntry(region, to);
    assertThat(stepped.effects()).containsAll(effects);
  }
}

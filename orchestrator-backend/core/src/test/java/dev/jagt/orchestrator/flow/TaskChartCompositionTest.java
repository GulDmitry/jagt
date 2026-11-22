package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.role.CheckerRole;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.statechart.Statechart;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskChartCompositionTest {

  private static final List<String> CORE =
      List.of(
          "lifecycle",
          "stop",
          "plan",
          "report",
          "turn",
          "deploy",
          "git",
          "repo",
          "request",
          "checks",
          "approval",
          "infra",
          "master",
          "session");

  @Test
  void chartWithNoRoleComposedIsValidAndNamesNoRoleRegion() {
    Statechart<TaskMove, ChartFacts, TaskEffect> chart =
        new TaskChart(new Composition(List.of())).chart();

    assertThat(chart.initial().toPlain().keySet()).containsExactlyInAnyOrderElementsOf(CORE);
  }

  @Test
  void eachRoleComposedAloneLeavesEveryCoreRegionAsDrawn() {
    Statechart<TaskMove, ChartFacts, TaskEffect> alone =
        new TaskChart(
                new Composition(
                    List.of(
                        new CheckerRegion(
                            new CheckerRole("lint", false, List.of("lint-read"), List.of())))))
            .chart();

    assertThat(alone.initial().toPlain().keySet())
        .containsAll(CORE)
        .contains("lint")
        .doesNotContain("acceptance");
  }

  @Test
  void everyRoleOnComposesOneRegionPerRole() {
    Statechart<TaskMove, ChartFacts, TaskEffect> chart =
        new TaskChart(
                new Composition(
                    List.of(
                        CheckerRegion.ACCEPTANCE,
                        new CheckerRegion(
                            new CheckerRole("lint", false, List.of("lint-read"), List.of())))))
            .chart();

    assertThat(chart.initial().toPlain())
        .containsEntry("acceptance", "NONE")
        .containsEntry("lint", "NONE");
  }

  @Test
  void chartWithTwoCheckersTellsWhoseRoundTheSessionDeclined() {
    Statechart<TaskMove, ChartFacts, TaskEffect> chart =
        new TaskChart(
                new Composition(
                    List.of(
                        CheckerRegion.ACCEPTANCE,
                        new CheckerRegion(
                            new CheckerRole("lint", false, List.of("lint-read"), List.of())))))
            .chart();
    Map<String, String> lintRound = new HashMap<>(chart.initial().toPlain());
    lintRound.put("lifecycle", TaskStatus.IN_PROGRESS.name());
    lintRound.put("turn", TurnKind.OPEN.name());
    lintRound.put("lint", CheckerPhase.ROUND_OPEN.name());
    ChartFacts facts = new ChartFacts(false, Map.of(), Facts.defaults(), ChartFacts.PROJECTED);

    Map<String, String> declinedByLint =
        chart
            .step(
                chart.configuration(lintRound),
                facts,
                new TaskMove.Checked.RoundDeclined(
                    "lint", AgentReport.NO_CHANGES, TaskStatus.IN_PROGRESS))
            .configuration()
            .toPlain();
    Map<String, String> declinedByAcceptance =
        chart
            .step(
                chart.configuration(lintRound),
                facts,
                new TaskMove.Checked.RoundDeclined(
                    "acceptance", AgentReport.NO_CHANGES, TaskStatus.IN_PROGRESS))
            .configuration()
            .toPlain();

    assertThat(declinedByLint).containsEntry("lifecycle", "DEPLOYED");
    assertThat(declinedByAcceptance).containsEntry("lifecycle", "IN_PROGRESS");
  }

  @Test
  void composedCheckerOpensItsReadOnItsOwnStepAndNotOnAcceptances() {
    Statechart<TaskMove, ChartFacts, TaskEffect> chart =
        new TaskChart(
                new Composition(
                    List.of(
                        CheckerRegion.ACCEPTANCE,
                        new CheckerRegion(
                            new CheckerRole("lint", false, List.of("lint-read"), List.of())))))
            .chart();
    Map<String, String> deployed = new HashMap<>(chart.initial().toPlain());
    deployed.put("lifecycle", TaskStatus.DEPLOYED.name());
    ChartFacts facts = new ChartFacts(false, Map.of(), Facts.defaults(), ChartFacts.PROJECTED);

    Map<String, String> onItsStep =
        chart
            .step(chart.configuration(deployed), facts, new TaskMove.Relayed("lint-read", "d", ""))
            .configuration()
            .toPlain();
    Map<String, String> onAcceptancesStep =
        chart
            .step(chart.configuration(deployed), facts, new TaskMove.Relayed("accept", "d", ""))
            .configuration()
            .toPlain();

    assertThat(onItsStep).containsEntry("lint", "READING").containsEntry("acceptance", "NONE");
    assertThat(onAcceptancesStep)
        .containsEntry("acceptance", "READING")
        .containsEntry("lint", "NONE");
  }

  @Test
  void twoCheckersNamingOneReadStepAreRefusedNamingTheStepAndBoth() {
    List<Role> roles =
        List.of(
            CheckerRegion.ACCEPTANCE,
            new CheckerRegion(new CheckerRole("lint", false, List.of("decline"), List.of())));

    assertThatThrownBy(() -> new Composition(roles))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContainingAll("decline", "acceptance", "lint");
  }

  @Test
  void theCheckersRunningJobsAreThoseWhoseRoleDeclaresThem() {
    Composition composition =
        new Composition(
            List.of(
                CheckerRegion.ACCEPTANCE,
                new CheckerRegion(new CheckerRole("lint", false, List.of("lint-read"), List.of())),
                new CheckerRegion(
                    new CheckerRole("audit", true, List.of("audit-read"), List.of()))));

    assertThat(composition.withJobs()).containsExactly("acceptance", "audit");
  }

  @Test
  void twoChartsOfDifferentCompositionsStandSideBySideInOneJvm() {
    TaskChart acceptanceOnly = new TaskChart(Composition.builtIn());
    TaskChart lintOnly =
        new TaskChart(
            Composition.of(
                List.of(new CheckerRole("lint", false, List.of("lint-read"), List.of()))));

    assertThat(acceptanceOnly.installedCheckers()).containsExactly("acceptance");
    assertThat(lintOnly.installedCheckers()).containsExactly("lint");
    assertThat(acceptanceOnly.chart().initial().toPlain()).doesNotContainKey("lint");
    assertThat(lintOnly.chart().initial().toPlain()).doesNotContainKey("acceptance");
  }

  @Test
  void chartKeepsTheCompositionItWasBuiltFrom() {
    Composition composition = new Composition(List.of());

    assertThat(new TaskChart(composition).composition()).isSameAs(composition);
  }

  @Test
  void taskWaitsOnTheHostWhilePollingChecksOrAwaitingItsDeploy() {
    assertThat(TaskChart.waitsOnTheHost(TaskStatus.CI_POLLING)).isTrue();
    assertThat(TaskChart.waitsOnTheHost(TaskStatus.IN_PROGRESS)).isFalse();
  }

  @Test
  void reportStandsThroughReadOnlyWhenItWaitsOnAnAnswer() {
    assertThat(TaskChart.standsThroughRead(AgentReport.QUESTION)).isTrue();
    assertThat(TaskChart.standsThroughRead(AgentReport.WAIT)).isFalse();
  }
}

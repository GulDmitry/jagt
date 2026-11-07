package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.statechart.Chart;
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
  void aChartWithNoRoleComposedIsValidAndNamesNoRoleRegion() {
    Chart<TaskMove, ChartFacts, TaskEffect> chart = TaskChart.of(new Composition(List.of()));

    assertThat(chart.initial().toPlain().keySet()).containsExactlyInAnyOrderElementsOf(CORE);
  }

  @Test
  void eachRoleComposedAloneLeavesEveryCoreRegionAsDrawn() {
    Chart<TaskMove, ChartFacts, TaskEffect> alone =
        TaskChart.of(
            new Composition(
                List.of(new CheckerRegion(new CheckerRole("lint", false, List.of("lint-read"))))));

    assertThat(alone.initial().toPlain().keySet())
        .containsAll(CORE)
        .contains("lint")
        .doesNotContain("qa");
  }

  @Test
  void everyRoleOnComposesOneRegionPerRole() {
    Chart<TaskMove, ChartFacts, TaskEffect> chart =
        TaskChart.of(
            new Composition(
                List.of(
                    CheckerRegion.QA,
                    new CheckerRegion(new CheckerRole("lint", false, List.of("lint-read"))))));

    assertThat(chart.initial().toPlain()).containsEntry("qa", "NONE").containsEntry("lint", "NONE");
  }

  @Test
  void aChartWithTwoCheckersTellsWhoseRoundTheSessionDeclined() {
    Chart<TaskMove, ChartFacts, TaskEffect> chart =
        TaskChart.of(
            new Composition(
                List.of(
                    CheckerRegion.QA,
                    new CheckerRegion(new CheckerRole("lint", false, List.of("lint-read"))))));
    Map<String, String> lintRound = new HashMap<>(chart.initial().toPlain());
    lintRound.put("lifecycle", TaskStatus.IN_PROGRESS.name());
    lintRound.put("turn", TurnKind.OPEN.name());
    lintRound.put("lint", CheckerPhase.ROUND_OPEN.name());
    ChartFacts facts = new ChartFacts(false, Map.of(), Facts.defaults());

    Map<String, String> declinedByLint =
        chart
            .step(
                chart.configuration(lintRound),
                facts,
                new TaskMove.Checked.RoundDeclined(
                    "lint", AgentReport.NO_CHANGES, TaskStatus.IN_PROGRESS))
            .configuration()
            .toPlain();
    Map<String, String> declinedByQa =
        chart
            .step(
                chart.configuration(lintRound),
                facts,
                new TaskMove.Checked.RoundDeclined(
                    "qa", AgentReport.NO_CHANGES, TaskStatus.IN_PROGRESS))
            .configuration()
            .toPlain();

    assertThat(declinedByLint).containsEntry("lifecycle", "DEPLOYED");
    assertThat(declinedByQa).containsEntry("lifecycle", "IN_PROGRESS");
  }

  @Test
  void aComposedCheckerOpensItsReadOnItsOwnStepAndNotOnQas() {
    Chart<TaskMove, ChartFacts, TaskEffect> chart =
        TaskChart.of(
            new Composition(
                List.of(
                    CheckerRegion.QA,
                    new CheckerRegion(new CheckerRole("lint", false, List.of("lint-read"))))));
    Map<String, String> deployed = new HashMap<>(chart.initial().toPlain());
    deployed.put("lifecycle", TaskStatus.DEPLOYED.name());
    ChartFacts facts = new ChartFacts(false, Map.of(), Facts.defaults());

    Map<String, String> onItsStep =
        chart
            .step(chart.configuration(deployed), facts, new TaskMove.Relayed("lint-read", "d", ""))
            .configuration()
            .toPlain();
    Map<String, String> onQasStep =
        chart
            .step(chart.configuration(deployed), facts, new TaskMove.Relayed("accept", "d", ""))
            .configuration()
            .toPlain();

    assertThat(onItsStep).containsEntry("lint", "READING").containsEntry("qa", "NONE");
    assertThat(onQasStep).containsEntry("qa", "READING").containsEntry("lint", "NONE");
  }

  @Test
  void twoCheckersNamingOneReadStepAreRefusedNamingTheStepAndBoth() {
    List<Role> roles =
        List.of(
            CheckerRegion.QA,
            new CheckerRegion(new CheckerRole("lint", false, List.of("decline"))));

    assertThatThrownBy(() -> new Composition(roles))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContainingAll("decline", "qa", "lint");
  }

  @Test
  void theCheckersRunningJobsAreThoseWhoseRoleDeclaresThem() {
    Composition composition =
        new Composition(
            List.of(
                CheckerRegion.QA,
                new CheckerRegion(new CheckerRole("lint", false, List.of("lint-read"))),
                new CheckerRegion(new CheckerRole("audit", true, List.of("audit-read")))));

    assertThat(composition.withJobs()).containsExactly("qa", "audit");
  }
}

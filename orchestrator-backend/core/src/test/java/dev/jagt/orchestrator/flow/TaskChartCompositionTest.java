package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.statechart.Chart;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskChartCompositionTest {

    private static final List<String> CORE = List.of("lifecycle", "stop", "plan", "report", "turn", "deploy", "git",
            "repo", "request", "checks", "approval", "infra", "master", "session");

    @Test
    void aChartWithNoRoleComposedIsValidAndNamesNoRoleRegion() {
        Chart<TaskMove, ChartFacts, TaskEffect> chart = TaskChart.of(new Composition(List.of()));

        assertThat(chart.initial().toPlain().keySet()).containsExactlyInAnyOrderElementsOf(CORE);
    }

    @Test
    void eachRoleComposedAloneLeavesEveryCoreRegionAsDrawn() {
        Chart<TaskMove, ChartFacts, TaskEffect> alone = TaskChart.of(new Composition(List.of(new CheckerRegion("lint"))));

        assertThat(alone.initial().toPlain().keySet()).containsAll(CORE).contains("lint").doesNotContain("qa");
    }

    @Test
    void everyRoleOnComposesOneRegionPerRole() {
        Chart<TaskMove, ChartFacts, TaskEffect> chart = TaskChart.of(
                new Composition(List.of(CheckerRegion.QA, new CheckerRegion("lint"))));

        assertThat(chart.initial().toPlain()).containsEntry("qa", "NONE").containsEntry("lint", "NONE");
    }

    @Test
    void aChartWithTwoCheckersTellsWhoseRoundTheSessionDeclined() {
        Chart<TaskMove, ChartFacts, TaskEffect> chart = TaskChart.of(
                new Composition(List.of(CheckerRegion.QA, new CheckerRegion("lint"))));
        Map<String, String> standing = new HashMap<>(chart.initial().toPlain());
        standing.put("lifecycle", TaskStatus.IN_PROGRESS.name());
        standing.put("turn", TurnKind.OPEN.name());
        TaskMove declined = new TaskMove.QaDeclined(AgentReport.NO_CHANGES, TaskStatus.IN_PROGRESS);
        ChartFacts facts = new ChartFacts(false, Map.of(), Facts.defaults());

        Map<String, String> lintRound = new HashMap<>(standing);
        lintRound.put("lint", CheckerPhase.ROUND_OPEN.name());
        Map<String, String> nobodysRound = new HashMap<>(standing);

        assertThat(chart.step(chart.configuration(lintRound), facts, declined).configuration().toPlain())
                .containsEntry("lifecycle", "DEPLOYED");
        assertThat(chart.step(chart.configuration(nobodysRound), facts, declined).configuration().toPlain())
                .containsEntry("lifecycle", "IN_PROGRESS");
    }
}

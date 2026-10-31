package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.statechart.Chart;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskChartEquivalenceProperties {

    @Property
    void aChartComposedFromTheBuiltInRolesStepsEveryMoveAsTheOneBuiltInCode(
            @ForAll TaskStatus status, @ForAll CheckerPhase qa, @ForAll CheckerPhase beneath, @ForAll TurnKind turn,
            @ForAll("moves") TaskMove move) {
        Chart<TaskMove, ChartFacts, TaskEffect> composed = TaskChart.of(Composition.builtIn());
        Chart<TaskMove, ChartFacts, TaskEffect> builtIn = TaskChart.chart();
        Map<String, String> standing = new HashMap<>(builtIn.initial().toPlain());
        standing.put("lifecycle", status.name());
        standing.put("qa", qa.name());
        standing.put("turn", turn.name());
        ChartFacts facts = new ChartFacts(false, Map.of("qa", beneath), Facts.defaults());

        var fromComposed = composed.step(composed.configuration(standing), facts, move);
        var fromBuiltIn = builtIn.step(builtIn.configuration(standing), facts, move);

        assertThat(fromComposed.configuration().toPlain()).isEqualTo(fromBuiltIn.configuration().toPlain());
        assertThat(fromComposed.effects()).isEqualTo(fromBuiltIn.effects());
    }

    @Property(tries = 1)
    void aChartComposedFromTheBuiltInRolesRendersAsTheOneBuiltInCode() {
        assertThat(TaskChart.of(Composition.builtIn()).render()).isEqualTo(TaskChart.chart().render());
    }

    @Provide
    Arbitrary<TaskMove> moves() {
        return Arbitraries.oneOf(
                Arbitraries.of(true, false).map(TaskMove.QaReadEnded::new),
                Arbitraries.of(true, false).map(standing -> new TaskMove.QaPassed("api@abc", standing)),
                Arbitraries.of(true, false).map(opens -> new TaskMove.QaRoundSent("d1", "- row", opens)),
                Arbitraries.of(AgentReport.class).map(report -> new TaskMove.QaDeclined(report, TaskStatus.IN_PROGRESS)),
                Arbitraries.of("accept", "decline", "again", "ship").map(step -> new TaskMove.Relayed(step, "d1", "a")),
                Arbitraries.just(new TaskMove.TurnClosed()),
                Arbitraries.of(TaskStatus.class).map(to -> new TaskMove.Reported(to, false)));
    }
}

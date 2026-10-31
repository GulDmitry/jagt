package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.QaVerdict;
import dev.jagt.statechart.ChartBuilder;
import dev.jagt.statechart.RegionId;

import java.util.EnumSet;

import static dev.jagt.orchestrator.task.TaskStatus.DEPLOYED;
import static dev.jagt.orchestrator.task.TaskStatus.IN_PROGRESS;

record CheckerRegion(String id) implements Role {

    static final CheckerRegion QA = new CheckerRegion("qa");

    @Override
    public Kind kind() {
        return Kind.CHECKER;
    }

    RegionId<CheckerPhase> region() {
        return new RegionId<>(id, CheckerPhase.class);
    }

    RegionId<CheckerPhase> beneath() {
        return new RegionId<>(id + " beneath a read", CheckerPhase.class);
    }

    @Override
    public void declareBeneath(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.derived(beneath(), facts -> facts.beneath(id));
    }

    @Override
    public void declareRegion(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(region(), CheckerPhase.NONE);
        for (CheckerPhase from : CheckerPhase.values()) {
            chart.onMatching(region(), from, "a " + id + " read opened", move -> move instanceof TaskMove.Relayed relayed
                            && QaVerdict.isStep(relayed.step()))
                    .when((others, move) -> others.get(TaskChart.LIFECYCLE) == DEPLOYED)
                    .emitFor(move -> new TaskEffect.OpensQaRead(((TaskMove.Relayed) move).step()))
                    .to(CheckerPhase.READING);
        }
        for (CheckerPhase under : EnumSet.of(CheckerPhase.NONE, CheckerPhase.ROUND_OPEN, CheckerPhase.PASSED)) {
            chart.onMatching(region(), CheckerPhase.READING, "the read ended, " + under + " beneath",
                            CheckerRegion::endsATurn)
                    .when((others, move) -> others.get(beneath()) == under)
                    .emit(new TaskEffect.ClosesQaRead()).to(under);
            chart.onMatching(region(), CheckerPhase.READING, "deployed change left, " + under + " beneath",
                            move -> !endsATurn(move))
                    .when((others, move) -> others.get(beneath()) == under && others.get(TaskChart.LIFECYCLE) == DEPLOYED
                            && TaskChart.land(DEPLOYED, move).filter(to -> to != DEPLOYED).isPresent())
                    .emit(new TaskEffect.ClosesQaRead()).to(under);
        }
        declareVerdicts(chart);
    }

    private void declareVerdicts(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        for (CheckerPhase from : CheckerPhase.values()) {
            boolean reading = from == CheckerPhase.READING;
            TaskChart.regionEdge(chart, region(), from, id + " passed, standing", move -> move instanceof TaskMove.QaPassed
                    passed && passed.standing(), CheckerRegion::passes, reading ? from : CheckerPhase.PASSED);
            TaskChart.regionEdge(chart, region(), from, id + " passed, not standing", move -> move instanceof TaskMove.QaPassed
                    passed && !passed.standing(), CheckerRegion::passes, from);
            TaskChart.regionEdge(chart, region(), from, id + " round sent, open", move -> move instanceof TaskMove.QaRoundSent
                    sent && sent.opens(), CheckerRegion::sendsRound, reading ? from : CheckerPhase.ROUND_OPEN);
            TaskChart.regionEdge(chart, region(), from, id + " round sent, not open", move -> move instanceof TaskMove.QaRoundSent
                    sent && !sent.opens(), CheckerRegion::sendsRound, from);
        }
    }

    @Override
    public void declareTurnEdge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from) {
        chart.onMatching(TaskChart.TURN, from, id + " read ended on its turn", move -> move instanceof TaskMove.QaReadEnded
                        ended && ended.holds()).emit(new TaskEffect.ClosesTurn()).to(TurnKind.CLOSED);
    }

    @Override
    public void declareLifecycleEdge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.onMatching(TaskChart.LIFECYCLE, IN_PROGRESS, id + " round declined", CheckerRegion::declinesARound)
                .when((others, move) -> others.get(region()) == CheckerPhase.ROUND_OPEN
                        && others.get(TaskChart.TURN) == TurnKind.OPEN)
                .emit(new TaskEffect.Lands(DEPLOYED)).to(DEPLOYED);
    }

    private static boolean declinesARound(TaskMove move) {
        return move instanceof TaskMove.QaDeclined declined && declined.means() == AgentReport.NO_CHANGES
                && declined.reported() == IN_PROGRESS;
    }

    private static boolean endsATurn(TaskMove move) {
        return move instanceof TaskMove.Relayed || move instanceof TaskMove.TurnClosed
                || move instanceof TaskMove.TurnRestored || move instanceof TaskMove.QaReadEnded;
    }

    private static TaskEffect passes(TaskMove move) {
        return new TaskEffect.PassesQa(((TaskMove.QaPassed) move).sha());
    }

    private static TaskEffect sendsRound(TaskMove move) {
        TaskMove.QaRoundSent sent = (TaskMove.QaRoundSent) move;
        return new TaskEffect.SendsQaRound(sent.digest(), sent.rows());
    }
}

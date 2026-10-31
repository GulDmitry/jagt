package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
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
                            && Checkers.roleOfRead(relayed.step()).filter(id::equals).isPresent())
                    .when((others, move) -> others.get(TaskChart.LIFECYCLE) == DEPLOYED)
                    .emitFor(move -> new TaskEffect.OpensCheckerRead(id, ((TaskMove.Relayed) move).step()))
                    .to(CheckerPhase.READING);
        }
        for (CheckerPhase under : EnumSet.of(CheckerPhase.NONE, CheckerPhase.ROUND_OPEN, CheckerPhase.PASSED)) {
            chart.onMatching(region(), CheckerPhase.READING, "the read ended, " + under + " beneath",
                            this::endsATurn)
                    .when((others, move) -> others.get(beneath()) == under)
                    .emit(new TaskEffect.ClosesCheckerRead(id)).to(under);
            chart.onMatching(region(), CheckerPhase.READING, "deployed change left, " + under + " beneath",
                            move -> !endsATurn(move))
                    .when((others, move) -> others.get(beneath()) == under && others.get(TaskChart.LIFECYCLE) == DEPLOYED
                            && TaskChart.land(DEPLOYED, move).filter(to -> to != DEPLOYED).isPresent())
                    .emit(new TaskEffect.ClosesCheckerRead(id)).to(under);
        }
        declareVerdicts(chart);
    }

    private void declareVerdicts(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        for (CheckerPhase from : CheckerPhase.values()) {
            boolean reading = from == CheckerPhase.READING;
            TaskChart.regionEdge(chart, region(), from, id + " passed, standing", move -> move instanceof TaskMove.Checked.Passed
                    passed && ownsAndStands(passed.role(), passed.standing()), CheckerRegion::passes, reading ? from : CheckerPhase.PASSED);
            TaskChart.regionEdge(chart, region(), from, id + " passed, not standing", move -> move instanceof TaskMove.Checked.Passed
                    passed && ownsAndStands(passed.role(), !passed.standing()), CheckerRegion::passes, from);
            TaskChart.regionEdge(chart, region(), from, id + " round sent, open", move -> move instanceof TaskMove.Checked.RoundSent
                    sent && ownsAndStands(sent.role(), sent.opens()), CheckerRegion::sendsRound, reading ? from : CheckerPhase.ROUND_OPEN);
            TaskChart.regionEdge(chart, region(), from, id + " round sent, not open", move -> move instanceof TaskMove.Checked.RoundSent
                    sent && ownsAndStands(sent.role(), !sent.opens()), CheckerRegion::sendsRound, from);
        }
    }

    @Override
    public void declareTurnEdge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from) {
        chart.onMatching(TaskChart.TURN, from, id + " read ended on its turn", move -> move instanceof TaskMove.Checked.ReadEnded
                        ended && id.equals(ended.role()) && ended.holds()).emit(new TaskEffect.ClosesTurn()).to(TurnKind.CLOSED);
    }

    @Override
    public void declareLifecycleEdge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.onMatching(TaskChart.LIFECYCLE, IN_PROGRESS, id + " round declined", this::declinesARound)
                .when((others, move) -> others.get(region()) == CheckerPhase.ROUND_OPEN
                        && others.get(TaskChart.TURN) == TurnKind.OPEN)
                .emit(new TaskEffect.Lands(DEPLOYED)).to(DEPLOYED);
    }

    private boolean declinesARound(TaskMove move) {
        return move instanceof TaskMove.Checked.RoundDeclined declined && id.equals(declined.role())
                && declined.means() == AgentReport.NO_CHANGES
                && declined.reported() == IN_PROGRESS;
    }

    private boolean endsATurn(TaskMove move) {
        return move instanceof TaskMove.Relayed || move instanceof TaskMove.TurnClosed
                || move instanceof TaskMove.TurnRestored
                || move instanceof TaskMove.Checked.ReadEnded ended && id.equals(ended.role());
    }

    private boolean ownsAndStands(String role, boolean stands) {
        return id.equals(role) && stands;
    }

    private static TaskEffect passes(TaskMove move) {
        TaskMove.Checked.Passed passed = (TaskMove.Checked.Passed) move;
        return new TaskEffect.PassesChecker(passed.role(), passed.sha());
    }

    private static TaskEffect sendsRound(TaskMove move) {
        TaskMove.Checked.RoundSent sent = (TaskMove.Checked.RoundSent) move;
        return new TaskEffect.SendsCheckerRound(sent.role(), sent.digest(), sent.rows());
    }
}

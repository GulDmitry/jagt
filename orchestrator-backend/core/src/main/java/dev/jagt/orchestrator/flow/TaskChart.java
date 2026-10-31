package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.statechart.Chart;
import dev.jagt.statechart.ChartBuilder;
import dev.jagt.statechart.ChartStep;
import dev.jagt.statechart.Condition;
import dev.jagt.statechart.Configuration;
import dev.jagt.statechart.EdgeBuilder;
import dev.jagt.statechart.RegionId;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static dev.jagt.orchestrator.task.TaskStatus.*;

/**
 * The lifecycle region, one edge per status move; the stop region, one way out per way a stop ends; the plan, report,
 * turn, deploy, session and git regions.
 */
final class TaskChart {

    static final RegionId<TaskStatus> LIFECYCLE = new RegionId<>("lifecycle", TaskStatus.class);

    private static final RegionId<StopKind> STOP = new RegionId<>("stop", StopKind.class);

    private static final RegionId<PlanGate> PLAN = new RegionId<>("plan", PlanGate.class);

    private static final RegionId<AgentReport> REPORT = new RegionId<>("report", AgentReport.class);

    static final RegionId<TurnKind> TURN = new RegionId<>("turn", TurnKind.class);

    private static final RegionId<DeployPhase> DEPLOY = new RegionId<>("deploy", DeployPhase.class);

    private static final RegionId<SessionPhase> SESSION = new RegionId<>("session", SessionPhase.class);

    private static final RegionId<GitPhase> GIT = new RegionId<>("git", GitPhase.class);

    private static final RegionId<RepoPhase> REPO = new RegionId<>("repo", RepoPhase.class);

    private static final RegionId<RequestPhase> REQUEST = new RegionId<>("request", RequestPhase.class);

    private static final RegionId<ChecksPhase> CHECKS = new RegionId<>("checks", ChecksPhase.class);

    private static final RegionId<ApprovalPhase> APPROVAL = new RegionId<>("approval", ApprovalPhase.class);

    private static final RegionId<InfraPhase> INFRA = new RegionId<>("infra", InfraPhase.class);

    private static final RegionId<AskStamp> ASK = new RegionId<>("ask", AskStamp.class);

    private static final Condition<TaskMove> ALWAYS = (others, move) -> true;

    private static final Set<TaskStatus> OPEN = EnumSet.complementOf(EnumSet.of(DONE));

    private static final Set<TaskStatus> HELD = EnumSet.of(REVERTED, DEPLOY_CONFLICT);

    private static final Set<TaskStatus> REPORTED = EnumSet.of(PLAN_PENDING, IN_PROGRESS, SHIPPING, REVIEW_PENDING,
            CI_FAILED, CI_POLLING);

    private static final Set<TaskStatus> BEFORE_THE_CODE = EnumSet.of(NEW, PLAN_PENDING, IN_PROGRESS);

    private static final Set<TaskStatus> BEFORE_THE_VERDICT = EnumSet.of(NEW, IN_PROGRESS, SHIPPING, REVIEW_PENDING,
            CI_POLLING, CI_FAILED);

    private static final Set<TaskStatus> READ = EnumSet.of(REVIEWED, APPROVED, CI_FAILED);

    private static final Set<TaskStatus> READ_FROM = EnumSet.of(CI_POLLING, REVIEWED, APPROVED, CI_FAILED);

    private static final Set<AgentReport> STANDING_THROUGH_A_READ = EnumSet.of(AgentReport.QUESTION,
            AgentReport.PERMISSION);

    private record Assembled(Composition composition, Chart<TaskMove, ChartFacts, TaskEffect> chart,
                             List<Configuration> starts) {

        static Assembled of(Composition composition) {
            Chart<TaskMove, ChartFacts, TaskEffect> chart = build(composition);
            return new Assembled(composition, chart, Arrays.stream(TaskStatus.values())
                    .map(status -> configurationOf(chart, composition, Phases.at(status))).toList());
        }
    }

    private static volatile Assembled current = Assembled.of(Composition.builtIn());

    private static boolean installed;

    private TaskChart() {
    }

    static Chart<TaskMove, ChartFacts, TaskEffect> chart() {
        return current.chart();
    }

    static List<String> installedCheckers() {
        return current.composition().checkers();
    }

    /** The composition every task is stepped on from now; set once, so a second call must name the same roles. */
    static synchronized void install(Composition composition) {
        if (installed && !current.composition().equals(composition)) {
            throw new IllegalStateException("roles are installed once: " + current.composition().checkers()
                    + " stand, not " + composition.checkers());
        }
        if (!installed) {
            current = Assembled.of(composition);
            installed = true;
        }
    }

    static Chart<TaskMove, ChartFacts, TaskEffect> of(Composition composition) {
        return build(composition);
    }

    /** What {@code move} does to a task standing at {@code phases}, in order; empty where no edge takes it. */
    static List<TaskEffect> effects(Phases phases, TaskMove move) {
        return step(phases, move).effects();
    }

    /** The configuration {@code move} leaves a task standing at {@code phases} in, with the effects it took. */
    static ChartStep<TaskEffect> step(Phases phases, TaskMove move) {
        Assembled now = current;
        return now.chart().step(configurationOf(now.chart(), now.composition(), phases), phases.facts(), move);
    }

    /** Whether a task at {@code status} with {@code facts} may be offered {@code action}. */
    static boolean offers(TaskStatus status, TaskAction action, Facts facts) {
        Assembled now = current;
        return now.chart().takes(LIFECYCLE, now.starts().get(status.ordinal()),
                new ChartFacts(false, Map.of(), facts), new TaskMove.Offers(action));
    }

    private static Configuration configurationOf(Chart<TaskMove, ChartFacts, TaskEffect> chart,
                                                 Composition composition, Phases phases) {
        Map<String, String> plain = new HashMap<>(corePhases(phases));
        composition.checkers().forEach(id -> plain.put(id, phases.checker(id).name()));
        return chart.configuration(plain);
    }

    private static Map<String, String> corePhases(Phases phases) {
        return Map.ofEntries(
                Map.entry(LIFECYCLE.name(), phases.lifecycle().name()), Map.entry(STOP.name(), phases.stop().name()),
                Map.entry(PLAN.name(), phases.plan().name()), Map.entry(REPORT.name(), phases.report().name()),
                Map.entry(TURN.name(), phases.turn().name()), Map.entry(DEPLOY.name(), phases.deploy().name()),
                Map.entry(SESSION.name(), phases.session().name()), Map.entry(GIT.name(), phases.git().name()),
                Map.entry(REPO.name(), phases.repo().name()), Map.entry(REQUEST.name(), phases.request().name()),
                Map.entry(CHECKS.name(), phases.checks().name()),
                Map.entry(APPROVAL.name(), phases.approval().name()), Map.entry(INFRA.name(), phases.infra().name()),
                Map.entry(MasterRegion.REGION.name(), phases.master().name()));
    }

    /** Where {@code move} leads a task at {@code from}, or empty where the chart has no edge for it. */
    static Optional<TaskStatus> land(TaskStatus from, TaskMove move) {
        return effects(Phases.at(from), move).stream().filter(TaskEffect.Lands.class::isInstance)
                .map(effect -> ((TaskEffect.Lands) effect).to()).findFirst();
    }

    /** Every status some action's outcome leads to; a task that is gone is written nothing. */
    static Set<TaskStatus> ranTargets() {
        Set<TaskStatus> targets = EnumSet.noneOf(TaskStatus.class);
        for (TaskAction action : TaskAction.values()) {
            for (TaskStatus from : TaskStatus.values()) {
                for (Outcome.Kind outcome : Outcome.Kind.values()) {
                    if (outcome != Outcome.Kind.GONE) {
                        land(from, new TaskMove.Ran(action, outcome)).ifPresent(targets::add);
                    }
                }
            }
        }
        return targets;
    }

    static boolean reportable(TaskStatus status) {
        return REPORTED.contains(status);
    }

    static boolean holds(TaskStatus status) {
        return HELD.contains(status);
    }

    static boolean standsThroughARead(AgentReport report) {
        return STANDING_THROUGH_A_READ.contains(report);
    }

    static Set<TaskStatus> reads() {
        return EnumSet.copyOf(READ);
    }

    private static Chart<TaskMove, ChartFacts, TaskEffect> build(Composition composition) {
        ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart = Chart.builder();
        chart.region(LIFECYCLE, NEW, Set.of(DONE));
        stops(chart);
        plans(chart);
        says(chart);
        turns(chart, composition);
        deploys(chart);
        gits(chart);
        repos(chart);
        requests(chart);
        checks(chart);
        approvals(chart);
        infras(chart);
        composition.roles().forEach(role -> role.declareRegion(chart));
        sessions(chart, composition);
        ran(chart);
        TaskOffers.declare(chart, LIFECYCLE);
        reports(chart);
        reads(chart);
        edge(chart, DEPLOYED, "deploy read red", new TaskMove.DeployRead(true)::equals, DEPLOY_CONFLICT);
        composition.roles().forEach(role -> role.declareLifecycleEdge(chart));
        edge(chart, VERIFYING, "verified", new TaskMove.Verified(true)::equals, REVIEW_PENDING);
        edge(chart, VERIFYING, "verification failed", new TaskMove.Verified(false)::equals, IN_PROGRESS);
        return chart.build();
    }

    private static void stops(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(STOP, StopKind.NONE);
        chart.onMatching(STOP, StopKind.NONE, "lift", new TaskMove.Lifted()::equals)
                .emit(new TaskEffect.ClearsStop()).to(StopKind.NONE);
        for (TaskStop.Trigger.Ends ends : TaskStop.Trigger.Ends.values()) {
            StopKind kind = StopKind.of(ends);
            chart.onMatching(STOP, StopKind.NONE, "stop ending on " + ends,
                            move -> move instanceof TaskMove.StopSet set && set.stop().trigger().ends() == ends)
                    .emitFor(move -> new TaskEffect.Stops(((TaskMove.StopSet) move).stop())).to(kind);
            chart.onMatching(STOP, kind, "lift", new TaskMove.Lifted()::equals)
                    .emit(new TaskEffect.ClearsStop()).to(StopKind.NONE);
            chart.onMatching(STOP, kind, ends + " ends it", new TaskMove.StopEnded(ends)::equals)
                    .emit(new TaskEffect.ClearsStop()).to(StopKind.NONE);
            chart.onMatching(STOP, kind, "a new request voids it", new TaskMove.RequestReplaced()::equals)
                    .emit(new TaskEffect.ClearsStop()).to(StopKind.NONE);
        }
    }

    private static void plans(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(PLAN, PlanGate.OPEN);
        chart.onMatching(PLAN, PlanGate.OPEN, "autoplan", new TaskMove.Autoplanned()::equals)
                .emit(new TaskEffect.GatesPlan(PlanGate.CLOSED)).to(PlanGate.CLOSED);
        for (PlanGate from : EnumSet.of(PlanGate.OPEN, PlanGate.CLOSED)) {
            chart.onMatching(PLAN, from, "plan mode", move -> move instanceof TaskMove.PlanAsked asked
                            && asked.planMode())
                    .when((others, move) -> planMayComeFirst(others.get(LIFECYCLE)))
                    .emit(new TaskEffect.GatesPlan(PlanGate.REQUIRED)).to(PlanGate.REQUIRED);
        }
        chart.onMatching(PLAN, PlanGate.CLOSED, "no plan declared", new TaskMove.NoPlanDeclared()::equals)
                .emit(new TaskEffect.GatesPlan(PlanGate.OPEN)).to(PlanGate.OPEN);
        for (PlanGate from : EnumSet.of(PlanGate.CLOSED, PlanGate.REQUIRED)) {
            chart.onMatching(PLAN, from, "plan approved", new TaskMove.PlanApproved()::equals)
                    .emit(new TaskEffect.GatesPlan(PlanGate.OPEN)).to(PlanGate.OPEN);
        }
    }

    private static void says(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(REPORT, AgentReport.PLAIN);
        for (AgentReport from : AgentReport.values()) {
            said(chart, from, "said on a status that holds its line", from.asks() ? AgentReport.PLAIN : from,
                    move -> move instanceof TaskMove.Said said && !said.means().asks(),
                    (others, move) -> HELD.contains(others.get(LIFECYCLE)));
            becomes(chart, from);
            if (from != AgentReport.PLAIN) {
                clears(chart, from);
            }
        }
    }

    private static void turns(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, Composition composition) {
        chart.region(TURN, TurnKind.CLOSED);
        for (TurnKind from : TurnKind.values()) {
            relayed(chart, from);
            EdgeBuilder<TaskMove, ChartFacts, TaskEffect, TurnKind> closed = chart.onMatching(TURN, from, "turn closed",
                    new TaskMove.TurnClosed()::equals).emit(new TaskEffect.ClosesTurn());
            if (from == TurnKind.CLOSED) {
                closed.stay();
            } else {
                closed.to(TurnKind.CLOSED);
            }
            composition.roles().forEach(role -> role.declareTurnEdge(chart, from));
            for (TurnKind restored : TurnKind.values()) {
                chart.onMatching(TURN, from, "turn restored " + restored, move -> move instanceof TaskMove.TurnRestored
                                undone && TurnKind.of(undone.before()) == restored)
                        .emitFor(move -> new TaskEffect.RestoresTurn(((TaskMove.TurnRestored) move).before()))
                        .to(restored);
            }
        }
    }

    private static void deploys(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(DEPLOY, DeployPhase.NONE);
        for (DeployPhase from : DeployPhase.values()) {
            deployed(chart, from, "opened", DeployPhase.LANDING, step -> step instanceof DeployStep.Opened);
        }
        for (DeployPhase from : EnumSet.of(DeployPhase.LANDING, DeployPhase.HANDED)) {
            deployed(chart, from, "landed", DeployPhase.LANDING, step -> step instanceof DeployStep.Merged
                    || step instanceof DeployStep.Fixed || step instanceof DeployStep.Pushed
                    || step instanceof DeployStep.Blocked);
        }
        for (DeployPhase from : DeployPhase.values()) {
            deployed(chart, from, "conflicted", DeployPhase.HANDED, step -> step instanceof DeployStep.Conflicted);
        }
        for (DeployPhase from : DeployPhase.values()) {
            deployed(chart, from, "reverted", DeployPhase.REVERTING, step -> step instanceof DeployStep.Reverted);
        }
        deployed(chart, DeployPhase.HANDED, "discarded", DeployPhase.LANDING, step -> step instanceof DeployStep.Discarded);
        deployed(chart, DeployPhase.HANDED, "watched", DeployPhase.HANDED, step -> step instanceof DeployStep.Seen
                || step instanceof DeployStep.Told || step instanceof DeployStep.Held);
        chart.onMatching(DEPLOY, DeployPhase.HANDED, "conflict left", move -> !(move instanceof TaskMove.DeployStepped))
                .when((others, move) -> others.get(LIFECYCLE) == DEPLOY_CONFLICT
                        && land(DEPLOY_CONFLICT, move).filter(to -> to != DEPLOY_CONFLICT).isPresent())
                .emit(new TaskEffect.Deploys(new DeployStep.Cleared())).to(DeployPhase.LANDING);
        deployed(chart, DeployPhase.LANDING, "asked", DeployPhase.CHECK_ASKED, step -> step instanceof DeployStep.Asked);
        for (DeployPhase at : EnumSet.of(DeployPhase.LANDING, DeployPhase.CHECK_ASKED)) {
            deployed(chart, at, "settled bare", at, step -> step instanceof DeployStep.Bare);
            deployed(chart, at, "read", at, step -> step instanceof DeployStep.ReadOn);
        }
    }

    private static void sessions(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, Composition composition) {
        chart.region(SESSION, SessionPhase.NONE);
        chart.derived(ASK, facts -> facts.stampedAsk() ? AskStamp.STAMPED : AskStamp.NONE);
        composition.roles().forEach(role -> role.declareBeneath(chart));
        EnumSet<SessionPhase> turnsEnd = EnumSet.of(SessionPhase.NONE, SessionPhase.WORKING, SessionPhase.IDLE,
                SessionPhase.QUIET);
        for (SessionPhase from : SessionPhase.values()) {
            startedOver(chart, from);
            chart.onMatching(SESSION, from, "work started clearing a stamped permission",
                            move -> move instanceof TaskMove.Signed signed
                                    && signed.sign() instanceof SessionSign.WorkStarted)
                    .when((others, move) -> others.get(REPORT) == AgentReport.PERMISSION
                            && others.get(ASK) == AskStamp.STAMPED)
                    .emitFor(move -> new TaskEffect.Signs(((TaskMove.Signed) move).sign()))
                    .raise(new TaskMove.Stamped(AgentReport.PLAIN))
                    .to(SessionPhase.WORKING);
            signed(chart, from, "work started", SessionPhase.WORKING, SessionSign.WorkStarted.class);
            chart.onMatching(SESSION, from, "asked", move -> move instanceof TaskMove.Signed signed
                            && signed.sign() instanceof SessionSign.Asked)
                    .when((others, move) -> others.get(REPORT) == AgentReport.PLAIN)
                    .emitFor(move -> new TaskEffect.Signs(((TaskMove.Signed) move).sign()))
                    .raise(new TaskMove.Stamped(AgentReport.PERMISSION))
                    .stay();
            signed(chart, from, "called", SessionPhase.WORKING, SessionSign.Called.class);
            signed(chart, from, "tool used", SessionPhase.WORKING, SessionSign.ToolUsed.class);
            signed(chart, from, "reported", from, SessionSign.Reported.class);
            signed(chart, from, "log named", from, SessionSign.LogNamed.class);
            read(chart, from, "read", turnsEnd.contains(from) ? SessionPhase.QUIET : from, false);
            read(chart, from, "read found no process", SessionPhase.GONE, true);
            signed(chart, from, "ended", SessionPhase.GONE, SessionSign.Ended.class);
            signed(chart, from, "restarted", SessionPhase.IDLE, SessionSign.Restarted.class);
            if (turnsEnd.contains(from)) {
                signed(chart, from, "turn ended", SessionPhase.IDLE, SessionSign.TurnEnded.class);
            }
            if (turnsEnd.contains(from) || from == SessionPhase.FAILED) {
                signed(chart, from, "turn failed", SessionPhase.FAILED, SessionSign.TurnFailed.class);
            }
        }
    }

    private static void gits(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(GIT, GitPhase.UNREAD);
        for (GitPhase from : GitPhase.values()) {
            EdgeBuilder<TaskMove, ChartFacts, TaskEffect, GitPhase> trusted = chart.onMatching(GIT, from, "trusted",
                            move -> move instanceof TaskMove.GitTrusted)
                    .emitFor(move -> new TaskEffect.TrustsGit(((TaskMove.GitTrusted) move).trust()));
            if (from == GitPhase.TRUSTED) {
                trusted.stay();
            } else {
                trusted.to(GitPhase.TRUSTED);
            }
        }
        chart.onMatching(GIT, GitPhase.TRUSTED, "changed", move -> move instanceof TaskMove.GitChanged)
                .emitFor(move -> new TaskEffect.ParksGit(((TaskMove.GitChanged) move).changed()))
                .to(GitPhase.CHANGED);
        chart.onMatching(GIT, GitPhase.CHANGED, "changed again", move -> move instanceof TaskMove.GitChanged)
                .emitFor(move -> new TaskEffect.ParksGit(((TaskMove.GitChanged) move).changed()))
                .stay();
        for (GitPhase from : EnumSet.of(GitPhase.TRUSTED, GitPhase.CHANGED)) {
            chart.onMatching(GIT, from, "repository joined", move -> move instanceof TaskMove.GitRepoJoined)
                    .emitFor(move -> new TaskEffect.JoinsGit(((TaskMove.GitRepoJoined) move).added())).stay();
        }
    }

    private static void repos(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(REPO, RepoPhase.NONE);
        chart.onMatching(REPO, RepoPhase.NONE, "asked", move -> move instanceof TaskMove.RepoAsked)
                .emitFor(move -> new TaskEffect.AsksRepo(((TaskMove.RepoAsked) move).project()))
                .to(RepoPhase.ASKED);
        chart.onMatching(REPO, RepoPhase.ASKED, "asked again", move -> move instanceof TaskMove.RepoAsked)
                .emitFor(move -> new TaskEffect.AsksRepo(((TaskMove.RepoAsked) move).project())).stay();
        chart.onMatching(REPO, RepoPhase.ASKED, "asked no more", new TaskMove.RepoUnasked()::equals)
                .emit(new TaskEffect.DropsRepoRequest()).to(RepoPhase.NONE);
        chart.onMatching(REPO, RepoPhase.ASKED, "attached", move -> move instanceof TaskMove.RepoAttached)
                .emitFor(move -> new TaskEffect.AttachesRepo(((TaskMove.RepoAttached) move).repo()))
                .to(RepoPhase.NONE);
        chart.onMatching(REPO, RepoPhase.NONE, "attached unasked", move -> move instanceof TaskMove.RepoAttached)
                .emitFor(move -> new TaskEffect.AttachesRepo(((TaskMove.RepoAttached) move).repo())).stay();
    }

    private static void requests(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(REQUEST, RequestPhase.NONE);
        for (RequestPhase from : RequestPhase.values()) {
            regionEdge(chart, REQUEST, from, "linked, open", move -> move instanceof TaskMove.RequestLinked linked
                    && linked.opens(), TaskChart::linksRequests, RequestPhase.OPEN);
            regionEdge(chart, REQUEST, from, "linked, standing", move -> move instanceof TaskMove.RequestLinked linked
                    && !linked.opens(), TaskChart::linksRequests, from);
        }
        chart.onMatching(REQUEST, RequestPhase.NONE, "repository added with a request", TaskChart::attachesARequest)
                .to(RequestPhase.OPEN);
        chart.onMatching(REQUEST, RequestPhase.MERGED, "repository added with a request merged",
                TaskChart::attachesAMergedRequest).stay();
        chart.onMatching(REQUEST, RequestPhase.MERGED, "repository added without one",
                move -> move instanceof TaskMove.RepoAttached).to(RequestPhase.OPEN);
        regionEdge(chart, REQUEST, RequestPhase.OPEN, "merged (all)", TaskChart::completesMerge,
                TaskChart::mergesRequest, RequestPhase.MERGED);
        regionEdge(chart, REQUEST, RequestPhase.OPEN, "merge read (some)", TaskChart::readsMerge,
                TaskChart::mergesRequest, RequestPhase.OPEN);
        regionEdge(chart, REQUEST, RequestPhase.MERGED, "merged (all)", TaskChart::completesMerge,
                TaskChart::mergesRequest, RequestPhase.MERGED);
        regionEdge(chart, REQUEST, RequestPhase.MERGED, "unmerged (some)", TaskChart::readsMerge,
                TaskChart::mergesRequest, RequestPhase.OPEN);
        regionEdge(chart, REQUEST, RequestPhase.NONE, "merge recorded unrequested", TaskChart::readsMerge,
                TaskChart::mergesRequest, RequestPhase.NONE);
    }

    private static boolean attachesARequest(TaskMove move) {
        return move instanceof TaskMove.RepoAttached attached && attached.repo().hasReviewRequest();
    }

    private static boolean attachesAMergedRequest(TaskMove move) {
        return attachesARequest(move) && ((TaskMove.RepoAttached) move).repo().wasMerged();
    }

    private static boolean readsMerge(TaskMove move) {
        return move instanceof TaskMove.MergedByHost || move instanceof TaskMove.MergedByGit;
    }

    private static boolean completesMerge(TaskMove move) {
        return move instanceof TaskMove.MergedByHost host && host.completes()
                || move instanceof TaskMove.MergedByGit git && git.completes();
    }

    private static TaskEffect linksRequests(TaskMove move) {
        TaskMove.RequestLinked linked = (TaskMove.RequestLinked) move;
        return new TaskEffect.LinksRequests(linked.byProject(), linked.url(), linked.newRound());
    }

    private static TaskEffect mergesRequest(TaskMove move) {
        return move instanceof TaskMove.MergedByGit git ? new TaskEffect.MergesRequest(git.project(), git.merged())
                : new TaskEffect.MergesRequestByHost(((TaskMove.MergedByHost) move).project());
    }

    private static void checks(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(CHECKS, ChecksPhase.NONE);
        for (ChecksPhase from : ChecksPhase.values()) {
            regionEdge(chart, CHECKS, from, "read on the head", move -> move instanceof TaskMove.ChecksRead read
                            && read.onHead(), TaskChart::readsChecks, ChecksPhase.READ);
            regionEdge(chart, CHECKS, from, "read on another head", move -> move instanceof TaskMove.ChecksRead read
                            && !read.onHead(), TaskChart::readsChecks, ChecksPhase.STALE);
            regionEdge(chart, CHECKS, from, "unread", move -> move instanceof TaskMove.ChecksUnread,
                    move -> new TaskEffect.FailsToReadChecks(((TaskMove.ChecksUnread) move).read()),
                    ChecksPhase.UNREAD);
        }
        for (ChecksPhase from : ChecksPhase.values()) {
            regionEdge(chart, CHECKS, from, "a new round", TaskChart::beginsARound,
                    move -> new TaskEffect.ResetsChecks(), ChecksPhase.NONE);
        }
        for (ChecksPhase from : EnumSet.of(ChecksPhase.NONE, ChecksPhase.UNREAD)) {
            regionEdge(chart, CHECKS, from, "head seen", move -> move instanceof TaskMove.HeadSeen,
                    TaskChart::seesHead, from);
        }
        for (ChecksPhase from : EnumSet.of(ChecksPhase.READ, ChecksPhase.STALE)) {
            regionEdge(chart, CHECKS, from, "head seen, reads of another", move -> move instanceof TaskMove.HeadSeen seen
                            && seen.staleReads(), TaskChart::seesHead, ChecksPhase.STALE);
            regionEdge(chart, CHECKS, from, "head seen, reads of it", move -> move instanceof TaskMove.HeadSeen seen
                            && !seen.staleReads(), TaskChart::seesHead, ChecksPhase.READ);
        }
    }

    private static boolean beginsARound(TaskMove move) {
        return move instanceof TaskMove.RequestLinked linked && (linked.newRound() || linked.relinks());
    }

    private static TaskEffect readsChecks(TaskMove move) {
        return new TaskEffect.ReadsChecks(((TaskMove.ChecksRead) move).hostStatus(), ((TaskMove.ChecksRead) move).tip());
    }

    private static TaskEffect seesHead(TaskMove move) {
        return new TaskEffect.SeesHead(((TaskMove.HeadSeen) move).tip());
    }

    private static void approvals(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(APPROVAL, ApprovalPhase.UNREAD);
        for (ApprovalPhase from : ApprovalPhase.values()) {
            regionEdge(chart, APPROVAL, from, "a new round", TaskChart::beginsARound,
                    move -> new TaskEffect.ResetsApproval(), ApprovalPhase.UNREAD);
            regionEdge(chart, APPROVAL, from, "approved", move -> move instanceof TaskMove.ApprovalRead read
                    && read.approved(), move -> new TaskEffect.ReadsApproval(true), ApprovalPhase.APPROVED);
            regionEdge(chart, APPROVAL, from, "withheld", move -> move instanceof TaskMove.ApprovalRead read
                    && !read.approved(), move -> new TaskEffect.ReadsApproval(false), ApprovalPhase.WITHHELD);
        }
    }

    private static void infras(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        chart.region(INFRA, InfraPhase.NONE);
        for (InfraPhase from : InfraPhase.values()) {
            regionEdge(chart, INFRA, from, "a new round", TaskChart::beginsARound,
                    move -> new TaskEffect.ResetsInfra(), InfraPhase.NONE);
            regionEdge(chart, INFRA, from, "armed", move -> move instanceof TaskMove.InfraArmed,
                    move -> new TaskEffect.ArmsInfra(((TaskMove.InfraArmed) move).retry()), InfraPhase.ARMED);
            regionEdge(chart, INFRA, from, "cleared with the sent", move -> move instanceof TaskMove.InfraCleared clear
                    && clear.all(), move -> new TaskEffect.ClearsInfra(true), InfraPhase.NONE);
            if (from != InfraPhase.ARMED) {
                regionEdge(chart, INFRA, from, "sent one cleared", move -> move instanceof TaskMove.InfraCleared clear
                        && !clear.all(), move -> new TaskEffect.ClearsInfra(false), from);
            }
            regionEdge(chart, INFRA, from, "sent", move -> move instanceof TaskMove.InfraSent sent
                    && sent.retry().attempt() < sent.retry().of(),
                    move -> new TaskEffect.SendsInfra(((TaskMove.InfraSent) move).retry()), InfraPhase.SENT);
            regionEdge(chart, INFRA, from, "last wait sent", move -> move instanceof TaskMove.InfraSent sent
                    && sent.retry().attempt() >= sent.retry().of(),
                    move -> new TaskEffect.SendsInfra(((TaskMove.InfraSent) move).retry()), InfraPhase.SPENT);
        }
        for (InfraPhase unarmed : EnumSet.of(InfraPhase.NONE, InfraPhase.SENT, InfraPhase.SPENT)) {
            regionEdge(chart, INFRA, InfraPhase.ARMED, "armed one cleared, " + unarmed + " remains",
                    move -> move instanceof TaskMove.InfraCleared clear && !clear.all() && clear.unarmed() == unarmed,
                    move -> new TaskEffect.ClearsInfra(false), unarmed);
            regionEdge(chart, INFRA, InfraPhase.ARMED, "head moved, " + unarmed + " remains",
                    move -> move instanceof TaskMove.HeadSeen seen && seen.moved() && seen.unarmed() == unarmed,
                    move -> new TaskEffect.DisarmsInfra(), unarmed);
        }
    }

    static <S extends Enum<S>> void regionEdge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart,
                                                       RegionId<S> region, S from, String label,
                                                       Predicate<TaskMove> matches,
                                                       Function<TaskMove, TaskEffect> effect, S to) {
        EdgeBuilder<TaskMove, ChartFacts, TaskEffect, S> edge = chart.onMatching(region, from, label, matches)
                .emitFor(effect);
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }

    private static void startedOver(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, SessionPhase from) {
        EdgeBuilder<TaskMove, ChartFacts, TaskEffect, SessionPhase> over = chart.onMatching(SESSION, from, "started over",
                new TaskMove.SessionStartedOver()::equals).emit(new TaskEffect.RestartsSession());
        if (from == SessionPhase.NONE) {
            over.stay();
        } else {
            over.to(SessionPhase.NONE);
        }
    }

    private static void read(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, SessionPhase from, String label,
                             SessionPhase to, boolean processGone) {
        EdgeBuilder<TaskMove, ChartFacts, TaskEffect, SessionPhase> edge = chart.onMatching(SESSION, from, label,
                        move -> move instanceof TaskMove.Signed signed && signed.sign() instanceof SessionSign.Read read
                                && read.processGone() == processGone)
                .emitFor(move -> new TaskEffect.Signs(((TaskMove.Signed) move).sign()));
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }

    private static void signed(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, SessionPhase from, String label,
                               SessionPhase to, Class<? extends SessionSign> kind) {
        EdgeBuilder<TaskMove, ChartFacts, TaskEffect, SessionPhase> edge = chart.onMatching(SESSION, from, label,
                        move -> move instanceof TaskMove.Signed signed && kind.isInstance(signed.sign()))
                .emitFor(move -> new TaskEffect.Signs(((TaskMove.Signed) move).sign()));
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }

    private static void deployed(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, DeployPhase from, String label,
                                 DeployPhase to, Predicate<DeployStep> matches) {
        EdgeBuilder<TaskMove, ChartFacts, TaskEffect, DeployPhase> edge = chart.onMatching(DEPLOY, from, label,
                        move -> move instanceof TaskMove.DeployStepped stepped && matches.test(stepped.step()))
                .emitFor(move -> new TaskEffect.Deploys(((TaskMove.DeployStepped) move).step()));
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }

    private static void relayed(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TurnKind from) {
        EdgeBuilder<TaskMove, ChartFacts, TaskEffect, TurnKind> relayed = chart.onMatching(TURN, from, "relayed",
                        move -> move instanceof TaskMove.Relayed)
                .emitFor(move -> new TaskEffect.OpensTurn(((TaskMove.Relayed) move).step(),
                        ((TaskMove.Relayed) move).digest(), ((TaskMove.Relayed) move).answered()));
        if (from == TurnKind.OPEN) {
            relayed.stay();
        } else {
            relayed.to(TurnKind.OPEN);
        }
    }

    private static void becomes(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, AgentReport from) {
        for (AgentReport means : AgentReport.values()) {
            if (means != from) {
                said(chart, from, "said " + means, means, move -> move instanceof TaskMove.Said said
                        && said.means() == means, ALWAYS);
                said(chart, from, "stamped " + means, means, move -> move instanceof TaskMove.Stamped stamped
                        && stamped.means() == means, ALWAYS);
            }
        }
    }

    private static void clears(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, AgentReport from) {
        said(chart, from, "verified", AgentReport.PLAIN, move -> move instanceof TaskMove.Verified, ALWAYS);
        said(chart, from, "deploy read moving the status", AgentReport.PLAIN,
                move -> move instanceof TaskMove.DeployRead, (others, move) -> land(others.get(LIFECYCLE), move)
                        .filter(to -> to != others.get(LIFECYCLE)).isPresent());
        if (!STANDING_THROUGH_A_READ.contains(from)) {
            said(chart, from, "read", AgentReport.PLAIN, move -> move instanceof TaskMove.Read, ALWAYS);
        }
    }

    private static void said(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, AgentReport from, String label,
                             AgentReport to, Predicate<TaskMove> matches, Condition<TaskMove> when) {
        EdgeBuilder<TaskMove, ChartFacts, TaskEffect, AgentReport> edge = chart.onMatching(REPORT, from, label, matches)
                .when(when).emit(new TaskEffect.Reports(to));
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }

    /** Whether a plan can still come first for a task in {@code status}: it has written nothing from the code yet. */
    static boolean planMayComeFirst(TaskStatus status) {
        return land(status, new TaskMove.Reported(PLAN_PENDING, false)).isPresent();
    }

    private static void ran(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        ran(chart, TaskAction.SHIP, Outcome.Kind.OK, CI_POLLING, TaskOffers.SHIP_FROM);
        ran(chart, TaskAction.SHIP, Outcome.Kind.RELAYED, SHIPPING, TaskOffers.SHIP_FROM);
        ran(chart, TaskAction.APPROVE_PLAN, Outcome.Kind.RELAYED, IN_PROGRESS, TaskOffers.APPROVE_PLAN_FROM);
        ran(chart, TaskAction.ADD_REPO, Outcome.Kind.RELAYED, IN_PROGRESS,
                EnumSet.complementOf(EnumSet.of(PLAN_PENDING, DEPLOYED, DEPLOY_CONFLICT, REVERTED, DONE)));
        ran(chart, TaskAction.DEPLOY, Outcome.Kind.OK, DEPLOYED, TaskOffers.DEPLOY_FROM);
        ran(chart, TaskAction.DEPLOY, Outcome.Kind.CONFLICT, DEPLOY_CONFLICT, TaskOffers.DEPLOY_FROM);
        ran(chart, TaskAction.REVERT, Outcome.Kind.OK, REVERTED, OPEN);
        ran(chart, TaskAction.REVERT, Outcome.Kind.CONFLICT, DEPLOY_CONFLICT, TaskOffers.DEPLOY_FROM);
        ran(chart, TaskAction.REVERT, Outcome.Kind.PARTIAL, DEPLOYED, EnumSet.complementOf(
                EnumSet.of(DEPLOY_CONFLICT, DONE)));
        ran(chart, TaskAction.DONE, Outcome.Kind.GONE, DONE, OPEN);
    }

    private static void ran(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TaskAction action,
                            Outcome.Kind outcome, TaskStatus to, Set<TaskStatus> from) {
        edges(chart, from, action.id() + " " + outcome, new TaskMove.Ran(action, outcome)::equals, to);
    }

    private static void reports(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        for (TaskStatus to : REPORTED) {
            Set<TaskStatus> free = EnumSet.noneOf(TaskStatus.class);
            OPEN.stream().filter(from -> mayReport(from, to) && !HELD.contains(from)).forEach(free::add);
            if (to == REVIEW_PENDING) {
                edges(chart, free, "report " + to + " owing verification", reportOf(to, true), VERIFYING);
            }
            edges(chart, free, "report " + to, reportOf(to, false), to);
            HELD.stream().filter(held -> mayReport(held, to)).forEach(held ->
                    edge(chart, held, "report " + to, reportOf(to, false), held));
        }
    }

    private static Predicate<TaskMove> reportOf(TaskStatus to, boolean owedOnly) {
        return move -> move instanceof TaskMove.Reported report && report.status() == to
                && (!owedOnly || report.verificationOwed());
    }

    private static boolean mayReport(TaskStatus from, TaskStatus to) {
        return (to != PLAN_PENDING || BEFORE_THE_CODE.contains(from))
                && (to != CI_POLLING || BEFORE_THE_VERDICT.contains(from));
    }

    private static void reads(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart) {
        for (TaskStatus concluded : READ) {
            edges(chart, READ_FROM, "read " + concluded, new TaskMove.Read(concluded)::equals, concluded);
        }
    }

    private static void edges(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, Set<TaskStatus> from, String label,
                              Predicate<TaskMove> matches, TaskStatus to) {
        if (!from.isEmpty()) {
            chart.onMatching(LIFECYCLE, from, label, matches).emit(new TaskEffect.Lands(to)).to(to);
        }
    }

    private static void edge(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, TaskStatus from, String label,
                             Predicate<TaskMove> matches, TaskStatus to) {
        EdgeBuilder<TaskMove, ChartFacts, TaskEffect, TaskStatus> edge = chart.onMatching(LIFECYCLE, from, label, matches)
                .emit(new TaskEffect.Lands(to));
        if (from == to) {
            edge.stay();
        } else {
            edge.to(to);
        }
    }
}

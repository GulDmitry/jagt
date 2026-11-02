package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.statechart.ChartBuilder;
import dev.jagt.statechart.Condition;
import dev.jagt.statechart.RegionId;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static dev.jagt.orchestrator.task.TaskStatus.*;

/** Which statuses offer which action, as self-loops of the lifecycle region guarded by the task's facts. */
final class TaskOffers {

    static final Set<TaskStatus> SHIP_FROM = EnumSet.of(IN_PROGRESS, VERIFYING, REVIEW_PENDING, SHIPPING,
            CI_POLLING, CI_FAILED, REVIEWED, DEPLOYED, REVERTED);

    static final Set<TaskStatus> DEPLOY_FROM = EnumSet.of(REVIEW_PENDING, CI_POLLING, CI_FAILED, REVIEWED,
            APPROVED, DEPLOYED, DEPLOY_CONFLICT);

    static final Set<TaskStatus> APPROVE_PLAN_FROM = EnumSet.of(PLAN_PENDING);

    private static final RegionId<Fact> REVIEW_REQUEST = new RegionId<>("review request", Fact.class);

    private static final RegionId<Fact> LIVE_DEPLOY = new RegionId<>("live deploy", Fact.class);

    private static final RegionId<Fact> AGENT_LIVE = new RegionId<>("agent live", Fact.class);

    private static final RegionId<Fact> GIT_CHANGED = new RegionId<>("git changed", Fact.class);

    private static final RegionId<Fact> STOPPED = new RegionId<>("stopped", Fact.class);

    private static final RegionId<Fact> REPO_REQUESTED = new RegionId<>("repo requested", Fact.class);

    private static final RegionId<Fact> REQUEST_MERGED = new RegionId<>("request merged", Fact.class);

    private static final RegionId<Fact> SHIPPED_WITHOUT_REQUEST = new RegionId<>("shipped without request", Fact.class);

    private static final RegionId<Fact> REVERT_CONFLICT = new RegionId<>("revert conflict", Fact.class);

    private record Need(RegionId<Fact> region, Fact is) {
    }

    private record Offer(TaskAction action, Set<TaskStatus> from, List<Need> needs) {
    }

    private static final Set<TaskStatus> ANY = EnumSet.allOf(TaskStatus.class);

    private static final Set<TaskStatus> OPEN = EnumSet.complementOf(EnumSet.of(DONE));

    private static final Set<TaskStatus> WRITTEN = EnumSet.of(IN_PROGRESS, VERIFYING, REVIEW_PENDING);

    private static final Set<TaskStatus> SHIP_ON_A_REQUEST = difference(SHIP_FROM, EnumSet.of(IN_PROGRESS, VERIFYING,
            REVIEW_PENDING, SHIPPING));

    private static final Set<TaskStatus> DEPLOY_ON_A_REQUEST = difference(DEPLOY_FROM, EnumSet.of(DEPLOY_CONFLICT));

    private static final Set<TaskStatus> DEPLOY_WITHOUT_A_REQUEST = EnumSet.of(REVIEW_PENDING);

    private static final Set<TaskStatus> REVERT_ON_A_LIVE_DEPLOY = difference(ANY, EnumSet.of(DEPLOYED,
            DEPLOY_CONFLICT));

    private static final List<Offer> OFFERS = List.of(
            offer(TaskAction.SHIP, WRITTEN, no(GIT_CHANGED), no(REQUEST_MERGED)),
            offer(TaskAction.SHIP, EnumSet.of(SHIPPING), no(GIT_CHANGED), no(REQUEST_MERGED), no(AGENT_LIVE)),
            offer(TaskAction.SHIP, SHIP_ON_A_REQUEST, no(GIT_CHANGED), no(REQUEST_MERGED), yes(REVIEW_REQUEST)),
            offer(TaskAction.APPROVE_PLAN, APPROVE_PLAN_FROM),
            offer(TaskAction.ADD_REPO, OPEN, yes(REPO_REQUESTED)),
            offer(TaskAction.SWEEP, ANY, yes(REVIEW_REQUEST)),
            offer(TaskAction.DEPLOY, EnumSet.of(DEPLOY_CONFLICT), no(GIT_CHANGED), no(REVERT_CONFLICT)),
            offer(TaskAction.DEPLOY, DEPLOY_ON_A_REQUEST, no(GIT_CHANGED), no(REVERT_CONFLICT),
                    yes(REVIEW_REQUEST)),
            offer(TaskAction.DEPLOY, DEPLOY_WITHOUT_A_REQUEST, no(GIT_CHANGED), no(REVERT_CONFLICT),
                    yes(SHIPPED_WITHOUT_REQUEST)),
            offer(TaskAction.REVERT, EnumSet.of(DEPLOYED, DEPLOY_CONFLICT), no(GIT_CHANGED)),
            offer(TaskAction.REVERT, REVERT_ON_A_LIVE_DEPLOY, no(GIT_CHANGED), yes(LIVE_DEPLOY)),
            offer(TaskAction.TRUST, ANY, yes(GIT_CHANGED)),
            offer(TaskAction.LIFT, ANY, yes(STOPPED)),
            offer(TaskAction.FOCUS, ANY),
            offer(TaskAction.IDE, ANY),
            offer(TaskAction.DIFF, ANY),
            offer(TaskAction.RESPAWN, ANY),
            offer(TaskAction.DONE, ANY));

    private TaskOffers() {
    }

    static boolean mentions(TaskAction action) {
        return OFFERS.stream().anyMatch(offer -> offer.action() == action);
    }

    static void declare(ChartBuilder<TaskMove, ChartFacts, TaskEffect> chart, RegionId<TaskStatus> lifecycle) {
        chart.derived(REVIEW_REQUEST, facts -> Fact.of(facts.facts().hasReviewRequest()));
        chart.derived(LIVE_DEPLOY, facts -> Fact.of(facts.facts().liveDeploy()));
        chart.derived(AGENT_LIVE, facts -> Fact.of(facts.facts().agentLive().getAsBoolean()));
        chart.derived(GIT_CHANGED, facts -> Fact.of(facts.facts().gitStateChanged()));
        chart.derived(STOPPED, facts -> Fact.of(facts.facts().stopped()));
        chart.derived(REPO_REQUESTED, facts -> Fact.of(facts.facts().repoRequested()));
        chart.derived(REQUEST_MERGED, facts -> Fact.of(facts.facts().requestMerged()));
        chart.derived(SHIPPED_WITHOUT_REQUEST, facts -> Fact.of(facts.facts().shippedWithoutRequest()));
        chart.derived(REVERT_CONFLICT, facts -> Fact.of(facts.facts().revertConflict()));
        for (Offer offer : OFFERS) {
            chart.onMatching(lifecycle, offer.from(), "offers " + offer.action().id(),
                            move -> move instanceof TaskMove.Offers offers && offers.action() == offer.action())
                    .when(holds(offer.needs())).stay();
        }
    }

    private static Condition<TaskMove> holds(List<Need> needs) {
        return (others, move) -> needs.stream().allMatch(need -> others.get(need.region()) == need.is());
    }

    private static Offer offer(TaskAction action, Set<TaskStatus> from, Need... needs) {
        return new Offer(action, from, List.of(needs));
    }

    private static Need yes(RegionId<Fact> region) {
        return new Need(region, Fact.YES);
    }

    private static Need no(RegionId<Fact> region) {
        return new Need(region, Fact.NO);
    }

    private static Set<TaskStatus> difference(Set<TaskStatus> all, Set<TaskStatus> removed) {
        Set<TaskStatus> left = EnumSet.copyOf(all);
        left.removeAll(removed);
        return left;
    }
}

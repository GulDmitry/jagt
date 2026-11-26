package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.task.TaskStatus.APPROVED;
import static dev.jagt.orchestrator.task.TaskStatus.CI_FAILED;
import static dev.jagt.orchestrator.task.TaskStatus.CI_POLLING;
import static dev.jagt.orchestrator.task.TaskStatus.DEPLOYED;
import static dev.jagt.orchestrator.task.TaskStatus.DEPLOY_CONFLICT;
import static dev.jagt.orchestrator.task.TaskStatus.DONE;
import static dev.jagt.orchestrator.task.TaskStatus.IN_PROGRESS;
import static dev.jagt.orchestrator.task.TaskStatus.NEW;
import static dev.jagt.orchestrator.task.TaskStatus.PLAN_PENDING;
import static dev.jagt.orchestrator.task.TaskStatus.REVERTED;
import static dev.jagt.orchestrator.task.TaskStatus.REVIEWED;
import static dev.jagt.orchestrator.task.TaskStatus.REVIEW_PENDING;
import static dev.jagt.orchestrator.task.TaskStatus.SHIPPING;
import static dev.jagt.orchestrator.task.TaskStatus.VERIFYING;

import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.statechart.DataId;
import dev.jagt.statechart.Guard;
import dev.jagt.statechart.RegionId;
import dev.jagt.statechart.StatechartBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Which statuses offer which action, as self-loops of the lifecycle region guarded by the task's
 * facts.
 */
final class TaskOffers {

  static final Set<TaskStatus> SHIP_FROM =
      EnumSet.of(
          IN_PROGRESS,
          VERIFYING,
          REVIEW_PENDING,
          SHIPPING,
          CI_POLLING,
          CI_FAILED,
          REVIEWED,
          DEPLOYED,
          REVERTED);

  static final Set<TaskStatus> DEPLOY_FROM =
      EnumSet.of(
          REVIEW_PENDING, CI_POLLING, CI_FAILED, REVIEWED, APPROVED, DEPLOYED, DEPLOY_CONFLICT);

  static final Set<TaskStatus> APPROVE_PLAN_FROM = EnumSet.of(PLAN_PENDING);

  private static final DataId<Fact> REVIEW_REQUEST = new DataId<>("review request", Fact.class);

  private static final DataId<Fact> LIVE_DEPLOY = new DataId<>("live deploy", Fact.class);

  private static final DataId<Fact> AGENT_LIVE = new DataId<>("agent live", Fact.class);

  private static final DataId<Fact> GIT_CHANGED = new DataId<>("git changed", Fact.class);

  private static final DataId<Fact> STOPPED = new DataId<>("stopped", Fact.class);

  private static final DataId<Fact> TIER_ABOVE = new DataId<>("tier above", Fact.class);

  private static final DataId<Fact> REPO_REQUESTED = new DataId<>("repo requested", Fact.class);

  private static final DataId<Fact> SEVERAL_REPOS = new DataId<>("several repos", Fact.class);

  private static final DataId<Fact> REQUEST_MERGED = new DataId<>("request merged", Fact.class);

  private static final DataId<Fact> SHIPPED_WITHOUT_REQUEST =
      new DataId<>("shipped without request", Fact.class);

  private static final DataId<Fact> REVERT_CONFLICT = new DataId<>("revert conflict", Fact.class);

  private static final DataId<Fact> SCENARIOS_READY = new DataId<>("scenarios ready", Fact.class);

  private static final DataId<Fact> CHECKS_GREEN = new DataId<>("checks green", Fact.class);

  private static final DataId<Fact> APPROVAL_REQUIRED =
      new DataId<>("approval required", Fact.class);

  private static final DataId<Fact> SHELL_ESCAPE = new DataId<>("shell escape", Fact.class);

  private static final DataId<Fact> DISTRUSTED = new DataId<>("git distrusted", Fact.class);

  private static final DataId<Fact> MERGED_NOW = new DataId<>("merged now", Fact.class);

  private static final DataId<Fact> STOP_HELD = new DataId<>("stop held", Fact.class);

  private static final DataId<Fact> FINDINGS_HELD = new DataId<>("findings held", Fact.class);

  private static final DataId<Fact> PLAN_HELD = new DataId<>("plan held", Fact.class);

  private record Need(DataId<Fact> region, Fact is) {}

  /** What only a press reads: the sentence it is declined with, empty where it may go on. */
  private record Press(
      DataId<Fact> region,
      Set<TaskAction> actions,
      Function<ChartFacts, Optional<String>> why,
      boolean quiet) {}

  private static final List<Press> PRESSES =
      List.of(
          new Press(
              DISTRUSTED,
              EnumSet.of(TaskAction.SHIP, TaskAction.DEPLOY, TaskAction.REVERT),
              facts -> facts.facts().workspace().distrust().get(),
              false),
          new Press(
              MERGED_NOW,
              EnumSet.of(TaskAction.SHIP),
              facts ->
                  facts.facts().delivery().mergedNow().getAsBoolean()
                      ? Optional.of("every request is already merged — nothing to ship")
                      : Optional.empty(),
              true),
          new Press(
              PLAN_HELD,
              EnumSet.of(TaskAction.SHIP),
              facts ->
                  facts.facts().hold().planClosed()
                      ? Optional.of("the plan is not approved — approve it before shipping")
                      : Optional.empty(),
              false),
          new Press(
              FINDINGS_HELD,
              EnumSet.of(TaskAction.SHIP),
              facts ->
                  facts.facts().delivery().hasReviewRequest()
                      ? Optional.empty()
                      : facts.facts().workspace().findingsOpen().get(),
              false),
          new Press(
              STOP_HELD,
              EnumSet.of(TaskAction.SHIP, TaskAction.DEPLOY),
              facts ->
                  !facts.human() && facts.facts().hold().stopped()
                      ? Optional.of("jagt stopped it; only a human presses it until `lift`")
                      : Optional.empty(),
              false));

  private record Offer(TaskAction action, Set<TaskStatus> from, List<Need> needs) {}

  private static final Set<TaskStatus> ANY = EnumSet.allOf(TaskStatus.class);

  private static final Set<TaskStatus> OPEN = EnumSet.complementOf(EnumSet.of(DONE));

  private static final Set<TaskStatus> STARTED =
      difference(ANY, EnumSet.of(NEW, PLAN_PENDING, DONE));

  private static final Set<TaskStatus> WRITTEN = EnumSet.of(IN_PROGRESS, VERIFYING, REVIEW_PENDING);

  private static final Set<TaskStatus> SHIP_ON_A_REQUEST =
      difference(SHIP_FROM, EnumSet.of(IN_PROGRESS, VERIFYING, REVIEW_PENDING, SHIPPING));

  private static final Set<TaskStatus> DEPLOY_ON_A_REQUEST =
      difference(DEPLOY_FROM, EnumSet.of(DEPLOY_CONFLICT, REVIEWED, APPROVED));

  private static final Set<TaskStatus> DEPLOY_WITHOUT_A_REQUEST = EnumSet.of(REVIEW_PENDING);

  private static final Set<TaskStatus> REVERT_ON_A_LIVE_DEPLOY =
      difference(ANY, EnumSet.of(DEPLOYED, DEPLOY_CONFLICT));

  static final Set<TaskStatus> HAND_OFF_FROM = difference(ANY, EnumSet.of(NEW, PLAN_PENDING, DONE));

  private static final Set<TaskStatus> RUN_FROM = difference(ANY, EnumSet.of(NEW, DONE));

  static final Set<TaskStatus> REMOVE_REPO_FROM =
      difference(ANY, EnumSet.of(NEW, DEPLOY_CONFLICT, DONE));

  private static final List<Offer> OFFERS =
      List.of(
          offer(TaskAction.SHIP, WRITTEN, no(GIT_CHANGED), no(REQUEST_MERGED)),
          offer(
              TaskAction.SHIP,
              EnumSet.of(SHIPPING),
              no(GIT_CHANGED),
              no(REQUEST_MERGED),
              no(AGENT_LIVE)),
          offer(
              TaskAction.SHIP,
              SHIP_ON_A_REQUEST,
              no(GIT_CHANGED),
              no(REQUEST_MERGED),
              yes(REVIEW_REQUEST)),
          offer(TaskAction.APPROVE_PLAN, APPROVE_PLAN_FROM),
          offer(TaskAction.ADD_REPO, OPEN, yes(REPO_REQUESTED)),
          offer(TaskAction.REMOVE_REPO, REMOVE_REPO_FROM, yes(SEVERAL_REPOS)),
          offer(TaskAction.HAND_OFF, HAND_OFF_FROM),
          offer(TaskAction.RUN, RUN_FROM, yes(SHELL_ESCAPE)),
          offer(TaskAction.SWEEP, ANY, yes(REVIEW_REQUEST)),
          offer(
              TaskAction.DEPLOY, EnumSet.of(DEPLOY_CONFLICT), no(GIT_CHANGED), no(REVERT_CONFLICT)),
          offer(
              TaskAction.DEPLOY,
              DEPLOY_ON_A_REQUEST,
              no(GIT_CHANGED),
              no(REVERT_CONFLICT),
              yes(REVIEW_REQUEST)),
          offer(
              TaskAction.DEPLOY,
              EnumSet.of(APPROVED),
              no(GIT_CHANGED),
              no(REVERT_CONFLICT),
              yes(REVIEW_REQUEST),
              yes(CHECKS_GREEN)),
          offer(
              TaskAction.DEPLOY,
              EnumSet.of(REVIEWED),
              no(GIT_CHANGED),
              no(REVERT_CONFLICT),
              yes(REVIEW_REQUEST),
              yes(CHECKS_GREEN),
              no(APPROVAL_REQUIRED)),
          offer(
              TaskAction.DEPLOY,
              DEPLOY_WITHOUT_A_REQUEST,
              no(GIT_CHANGED),
              no(REVERT_CONFLICT),
              yes(SHIPPED_WITHOUT_REQUEST)),
          offer(TaskAction.REVERT, EnumSet.of(DEPLOYED, DEPLOY_CONFLICT), no(GIT_CHANGED)),
          offer(TaskAction.REVERT, REVERT_ON_A_LIVE_DEPLOY, no(GIT_CHANGED), yes(LIVE_DEPLOY)),
          offer(TaskAction.TRUST, ANY, yes(GIT_CHANGED)),
          offer(TaskAction.PAUSE, STARTED, no(STOPPED)),
          offer(TaskAction.LIFT, ANY, yes(STOPPED)),
          offer(TaskAction.ESCALATE, ANY, yes(STOPPED), yes(TIER_ABOVE)),
          offer(TaskAction.SCENARIOS, OPEN, no(LIVE_DEPLOY), yes(SCENARIOS_READY)),
          offer(TaskAction.FOCUS, ANY),
          offer(TaskAction.CODE, ANY),
          offer(TaskAction.DIFF, ANY),
          offer(TaskAction.RESPAWN, ANY),
          offer(TaskAction.DONE, ANY));

  private TaskOffers() {}

  static boolean mentions(TaskAction action) {
    return OFFERS.stream().anyMatch(offer -> offer.action() == action);
  }

  static void declare(
      StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart, RegionId<TaskStatus> lifecycle) {
    chart.datum(REVIEW_REQUEST, facts -> Fact.of(facts.facts().delivery().hasReviewRequest()));
    chart.datum(LIVE_DEPLOY, facts -> Fact.of(facts.facts().delivery().liveDeploy()));
    chart.datum(AGENT_LIVE, facts -> Fact.of(facts.facts().hold().agentLive().getAsBoolean()));
    chart.datum(GIT_CHANGED, facts -> Fact.of(facts.facts().workspace().gitStateChanged()));
    chart.datum(STOPPED, facts -> Fact.of(facts.facts().hold().stopped()));
    chart.datum(TIER_ABOVE, facts -> Fact.of(facts.facts().hold().tierAbove()));
    chart.datum(REPO_REQUESTED, facts -> Fact.of(facts.facts().workspace().repoRequested()));
    chart.datum(SEVERAL_REPOS, facts -> Fact.of(facts.facts().workspace().severalRepos()));
    chart.datum(REQUEST_MERGED, facts -> Fact.of(facts.facts().delivery().requestMerged()));
    chart.datum(
        SHIPPED_WITHOUT_REQUEST,
        facts -> Fact.of(facts.facts().delivery().shippedWithoutRequest()));
    chart.datum(REVERT_CONFLICT, facts -> Fact.of(facts.facts().delivery().revertConflict()));
    chart.datum(SCENARIOS_READY, facts -> Fact.of(facts.facts().reach().scenarios()));
    chart.datum(CHECKS_GREEN, facts -> Fact.of(facts.facts().delivery().checksGreen()));
    chart.datum(APPROVAL_REQUIRED, facts -> Fact.of(facts.facts().reach().requireApproval()));
    chart.datum(SHELL_ESCAPE, facts -> Fact.of(facts.facts().reach().shellEscape()));
    for (Press press : PRESSES) {
      chart.datum(press.region(), facts -> Fact.of(press.why().apply(facts).isPresent()));
    }
    for (Offer offer : OFFERS) {
      chart
          .onMatching(
              lifecycle,
              offer.from(),
              "offers " + offer.action().id(),
              move -> move instanceof TaskMove.Offers offers && offers.action() == offer.action())
          .when(holds(offer.needs()))
          .internal();
    }
  }

  private static Guard<TaskMove> holds(List<Need> needs) {
    return (active, move) ->
        needs.stream().allMatch(need -> active.datum(need.region()) == need.is());
  }

  private static Offer offer(TaskAction action, Set<TaskStatus> from, Need... needs) {
    List<Need> all = new ArrayList<>(List.of(needs));
    PRESSES.stream()
        .filter(press -> press.actions().contains(action))
        .forEach(press -> all.add(no(press.region())));
    return new Offer(action, from, List.copyOf(all));
  }

  /** The first sentence a press declines {@code action} with, in the order the presses are read. */
  static Optional<Decline> declined(TaskAction action, ChartFacts facts) {
    for (Press press : PRESSES) {
      if (press.actions().contains(action)) {
        Optional<String> why = press.why().apply(facts);
        if (why.isPresent()) {
          return Optional.of(new Decline(why.get(), press.quiet()));
        }
      }
    }
    return Optional.empty();
  }

  private static Need yes(DataId<Fact> region) {
    return new Need(region, Fact.YES);
  }

  private static Need no(DataId<Fact> region) {
    return new Need(region, Fact.NO);
  }

  private static Set<TaskStatus> difference(Set<TaskStatus> all, Set<TaskStatus> removed) {
    Set<TaskStatus> left = EnumSet.copyOf(all);
    left.removeAll(removed);
    return left;
  }
}

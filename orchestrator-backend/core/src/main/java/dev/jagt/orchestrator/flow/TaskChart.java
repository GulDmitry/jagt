package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.ChartRegions.APPROVAL;
import static dev.jagt.orchestrator.flow.ChartRegions.CHECKS;
import static dev.jagt.orchestrator.flow.ChartRegions.DEPLOY;
import static dev.jagt.orchestrator.flow.ChartRegions.GIT;
import static dev.jagt.orchestrator.flow.ChartRegions.INFRA;
import static dev.jagt.orchestrator.flow.ChartRegions.LIFECYCLE;
import static dev.jagt.orchestrator.flow.ChartRegions.PLAN;
import static dev.jagt.orchestrator.flow.ChartRegions.REPO;
import static dev.jagt.orchestrator.flow.ChartRegions.REPORT;
import static dev.jagt.orchestrator.flow.ChartRegions.REQUEST;
import static dev.jagt.orchestrator.flow.ChartRegions.SESSION;
import static dev.jagt.orchestrator.flow.ChartRegions.STOP;
import static dev.jagt.orchestrator.flow.ChartRegions.TURN;
import static dev.jagt.orchestrator.task.TaskStatus.APPROVED;
import static dev.jagt.orchestrator.task.TaskStatus.CI_FAILED;
import static dev.jagt.orchestrator.task.TaskStatus.DONE;
import static dev.jagt.orchestrator.task.TaskStatus.NEW;
import static dev.jagt.orchestrator.task.TaskStatus.PLAN_PENDING;
import static dev.jagt.orchestrator.task.TaskStatus.REVIEWED;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.deploy.RedCause;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.statechart.Configuration;
import dev.jagt.statechart.Macrostep;
import dev.jagt.statechart.Statechart;
import dev.jagt.statechart.StatechartBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The task's statechart: the lifecycle region and the regions of {@link ProgressRegions} and {@link
 * DeliveryRegions}, composed with the roles' own.
 */
public final class TaskChart {

  private final Composition composition;
  private final Statechart<TaskMove, ChartFacts, TaskEffect> chart;
  private final Map<TaskStatus, Configuration> starts;
  private volatile @Nullable Set<TaskStatus> ranTargets;

  public TaskChart(Composition composition) {
    this.composition = composition;
    this.chart = build(composition);
    this.starts =
        Arrays.stream(TaskStatus.values())
            .collect(
                Collectors.toUnmodifiableMap(
                    status -> status,
                    status -> configurationOf(chart, composition, Phases.at(status))));
  }

  Statechart<TaskMove, ChartFacts, TaskEffect> chart() {
    return chart;
  }

  Composition composition() {
    return composition;
  }

  List<String> installedCheckers() {
    return composition.checkers();
  }

  /**
   * What {@code move} does to a task standing at {@code phases}, in order; empty where no edge
   * takes it.
   */
  List<TaskEffect> effects(Phases phases, TaskMove move) {
    return step(phases, move).effects();
  }

  /**
   * The configuration {@code move} leaves a task standing at {@code phases} in, with the effects it
   * took.
   */
  Macrostep<TaskEffect> step(Phases phases, TaskMove move) {
    return chart.step(configurationOf(chart, composition, phases), phases.facts(), move);
  }

  /** Whether a task at {@code status} with {@code facts} may be offered {@code action}. */
  boolean offers(TaskStatus status, TaskAction action, Facts facts) {
    return offers(status, action, facts, ChartFacts.PROJECTED);
  }

  /** As above, for a press by {@code origin}. */
  boolean offers(TaskStatus status, TaskAction action, Facts facts, @Nullable ActionOrigin origin) {
    return chart.takes(
        LIFECYCLE,
        Objects.requireNonNull(starts.get(status)),
        new ChartFacts(false, Map.of(), facts, origin),
        new TaskMove.Offers(action));
  }

  /** Why a press by {@code origin} is declined once the chart has not offered it. */
  Optional<Decline> declined(TaskAction action, Facts facts, @Nullable ActionOrigin origin) {
    return TaskOffers.declined(action, new ChartFacts(false, Map.of(), facts, origin));
  }

  private static Configuration configurationOf(
      Statechart<TaskMove, ChartFacts, TaskEffect> chart, Composition composition, Phases phases) {
    Map<String, String> plain = new HashMap<>(corePhases(phases));
    composition.checkers().forEach(id -> plain.put(id, phases.checker(id).name()));
    return chart.configuration(plain);
  }

  private static Map<String, String> corePhases(Phases phases) {
    return Map.ofEntries(
        Map.entry(LIFECYCLE.name(), phases.progress().lifecycle().name()),
        Map.entry(STOP.name(), phases.progress().stop().name()),
        Map.entry(PLAN.name(), phases.progress().plan().name()),
        Map.entry(REPORT.name(), phases.progress().report().name()),
        Map.entry(TURN.name(), phases.progress().turn().name()),
        Map.entry(DEPLOY.name(), phases.delivery().deploy().name()),
        Map.entry(SESSION.name(), phases.progress().session().name()),
        Map.entry(GIT.name(), phases.delivery().git().name()),
        Map.entry(REPO.name(), phases.delivery().repo().name()),
        Map.entry(REQUEST.name(), phases.delivery().request().name()),
        Map.entry(CHECKS.name(), phases.gates().checks().name()),
        Map.entry(APPROVAL.name(), phases.gates().approval().name()),
        Map.entry(INFRA.name(), phases.gates().infra().name()),
        Map.entry(MasterRegion.REGION.name(), phases.watchers().master().name()));
  }

  /**
   * Where {@code move} leads a task at {@code from}, or empty where the chart has no edge for it.
   */
  Optional<TaskStatus> land(TaskStatus from, TaskMove move) {
    return effects(Phases.at(from), move).stream()
        .filter(TaskEffect.Lands.class::isInstance)
        .map(effect -> ((TaskEffect.Lands) effect).to())
        .findFirst();
  }

  /** As above, for a task whose plan gate stands at {@code plan}. */
  Optional<TaskStatus> land(TaskStatus from, PlanGate plan, TaskMove move) {
    return effects(Phases.at(from, plan), move).stream()
        .filter(TaskEffect.Lands.class::isInstance)
        .map(effect -> ((TaskEffect.Lands) effect).to())
        .findFirst();
  }

  /** Every status some action's outcome leads to; a task that is gone is written nothing. */
  Set<TaskStatus> ranTargets() {
    Set<TaskStatus> kept = ranTargets;
    if (kept == null) {
      kept = Collections.unmodifiableSet(compute());
      ranTargets = kept;
    }
    return kept;
  }

  private Set<TaskStatus> compute() {
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

  /**
   * Statuses a verified hand-back is redirected from: the only way into them is a report that owes
   * verification.
   */
  Set<TaskStatus> verifiable() {
    Set<TaskStatus> from = EnumSet.noneOf(TaskStatus.class);
    for (TaskStatus status : TaskStatus.values()) {
      if (land(status, new TaskMove.Verified(true)).isPresent()) {
        from.add(status);
      }
    }
    return from;
  }

  /**
   * What a read of the review round concludes: a red run stops it unless it died on infrastructure
   * with a retry left.
   */
  static Optional<TaskStatus> readConcludes(
      boolean unresolved, boolean approved, Pipeline checks, RedCause cause, boolean retryLeft) {
    if (checks == Pipeline.RED) {
      return cause == RedCause.INFRASTRUCTURE && retryLeft
          ? Optional.empty()
          : Optional.of(CI_FAILED);
    }
    if (unresolved) {
      return Optional.empty();
    }
    if (approved) {
      return Optional.of(APPROVED);
    }
    return checks == Pipeline.GREEN ? Optional.of(REVIEWED) : Optional.empty();
  }

  private Statechart<TaskMove, ChartFacts, TaskEffect> build(Composition composition) {
    StatechartBuilder<TaskMove, ChartFacts, TaskEffect> chart = Statechart.builder();
    ProgressRegions progress = new ProgressRegions(composition, this::land, this::planMayComeFirst);
    final DeliveryRegions delivery = new DeliveryRegions(this::land);
    chart.region(LIFECYCLE, NEW, Set.of(DONE));
    progress.stops(chart);
    progress.plans(chart);
    progress.says(chart);
    progress.turns(chart);
    delivery.deploys(chart);
    delivery.gits(chart);
    delivery.repos(chart);
    delivery.requests(chart);
    delivery.checks(chart);
    delivery.approvals(chart);
    delivery.infras(chart);
    composition.roles().forEach(role -> role.declareRegion(chart, this::land));
    progress.sessions(chart);
    LifecycleEdges.ran(chart);
    TaskOffers.declare(chart, LIFECYCLE);
    LifecycleEdges.reports(chart);
    LifecycleEdges.reads(chart);
    LifecycleEdges.conflicts(chart);
    composition.roles().forEach(role -> role.declareLifecycleEdge(chart));
    LifecycleEdges.verification(chart);
    return chart.build();
  }

  /**
   * Whether a plan can still come first for a task in {@code status}: it has written nothing from
   * the code yet.
   */
  boolean planMayComeFirst(TaskStatus status) {
    return land(status, new TaskMove.Reported(PLAN_PENDING, false)).isPresent();
  }
}

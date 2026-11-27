package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.ChartRegions.APPROVAL;
import static dev.jagt.orchestrator.flow.ChartRegions.CHECKS;
import static dev.jagt.orchestrator.flow.ChartRegions.DEPLOY;
import static dev.jagt.orchestrator.flow.ChartRegions.INFRA;
import static dev.jagt.orchestrator.flow.ChartRegions.LIFECYCLE;
import static dev.jagt.orchestrator.flow.ChartRegions.REPORT;
import static dev.jagt.orchestrator.flow.ChartRegions.REQUEST;
import static dev.jagt.orchestrator.flow.ChartRegions.STOP;

import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.deploy.ApprovalPhase;
import dev.jagt.orchestrator.flow.deploy.ChecksPhase;
import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.deploy.RedCause;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.flow.session.StopHold;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.Turn.Holder;
import dev.jagt.statechart.Configuration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The one decider of progress: per tick, what each task's steps in flight call for, a missed beat
 * past {@code beat} spending its row of {@link StepPolicy}.
 */
record Reconciler(Composition composition, Arbiter.Profile profile, Duration beat) {

  /** The stored facts a decision reads: the chart, the last pipeline reads and the beats. */
  record Stored(
      Configuration chart, Pipeline checks, Pipeline deploy, Map<ProgressStep, Beat> beats) {}

  sealed interface Decision {

    record Stay() implements Decision {}

    /** A move the stored facts already call for, taken through the chart like any other. */
    record Land(ProgressStep step, TaskMove move) implements Decision {}

    record Retry(ProgressStep step) implements Decision {}

    record Restart(ProgressStep step) implements Decision {}

    record Stop(ProgressStep step, TaskMove move) implements Decision {}
  }

  Decision decide(Stored task, long now) {
    List<ProgressStep> steps = inFlight(task);
    for (ProgressStep step : steps) {
      Optional<TaskMove> landing = landing(step, task);
      if (landing.isPresent()) {
        return new Decision.Land(step, landing.get());
      }
    }
    return steps.stream()
        .map(step -> missed(step, task.beats().get(step), now))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseGet(Decision.Stay::new);
  }

  /** A stopped task keeps only the step whose read can end its stop. */
  List<ProgressStep> inFlight(Stored task) {
    Configuration chart = task.chart();
    StopKind stop = chart.get(STOP);
    List<ProgressStep> running = running(task);
    if (stop == StopKind.NONE) {
      return running;
    }
    return running.stream().filter(step -> endsOn(step) == stop).toList();
  }

  Holder holder(Stored task) {
    Configuration chart = task.chart();
    return Turns.next(
        chart.get(LIFECYCLE),
        chart.get(REPORT),
        chart.get(STOP) == StopKind.NONE ? StopHold.NONE : StopHold.HUMAN,
        checkers(chart),
        profile.at(chart.get(MasterRegion.REGION)),
        facts(task));
  }

  private static Facts facts(Stored task) {
    Configuration chart = task.chart();
    boolean checksGreen =
        chart.get(CHECKS) != ChecksPhase.UNREAD
            && (task.checks() == Pipeline.GREEN || task.checks() == Pipeline.NONE);
    return Facts.defaults()
        .withDelivery(
            Facts.Delivery.defaults()
                .withHasReviewRequest(chart.get(REQUEST) != RequestPhase.NONE)
                .withChecksGreen(checksGreen));
  }

  private List<ProgressStep> running(Stored task) {
    Configuration chart = task.chart();
    TaskStatus status = chart.get(LIFECYCLE);
    Holder holder = holder(task);
    return ProgressStep.ALL.stream()
        .filter(step ->
            switch (step) {
              case ProgressStep.SessionTurn turn -> Holder.SESSION.equals(holder);
              case ProgressStep.Verifying verification -> status == TaskStatus.VERIFYING;
              case ProgressStep.HostRead read ->
                  chart.get(REQUEST) == RequestPhase.OPEN && StatusSets.READ_FROM.contains(status);
              case ProgressStep.InfraRerun rerun -> chart.get(INFRA) == InfraPhase.ARMED;
              case ProgressStep.JudgeRound round ->
                  Holder.ARBITER.equals(holder)
                      && chart.get(MasterRegion.REGION) == MasterPhase.STANDS_IN;
              case ProgressStep.Deploy deploy ->
                  deploying(chart) && task.deploy() != Pipeline.GREEN;
            })
        .toList();
  }

  private static boolean deploying(Configuration chart) {
    DeployPhase phase = chart.get(DEPLOY);
    return chart.get(LIFECYCLE) == TaskStatus.DEPLOYED
        && (phase == DeployPhase.LANDING || phase == DeployPhase.CHECK_ASKED);
  }

  private static StopKind endsOn(ProgressStep step) {
    return switch (step) {
      case ProgressStep.HostRead read -> StopKind.HOST_READ;
      case ProgressStep.Deploy deploy -> StopKind.DEPLOY_READ;
      default -> StopKind.NONE;
    };
  }

  private Optional<TaskMove> landing(ProgressStep step, Stored task) {
    Configuration chart = task.chart();
    return switch (step) {
      case ProgressStep.HostRead read when chart.get(CHECKS) == ChecksPhase.READ ->
          TaskChart.readConcludes(
                  false,
                  chart.get(APPROVAL) == ApprovalPhase.APPROVED,
                  task.checks(),
                  RedCause.CODE,
                  false)
              .filter(concluded -> concluded != chart.get(LIFECYCLE))
              .map(TaskMove.Read::new);
      case ProgressStep.Deploy deploy
          when task.deploy() == Pipeline.RED && spent(deploy, task.beats().get(deploy)) ->
          Optional.of(new TaskMove.DeployRead(true));
      default -> Optional.empty();
    };
  }

  private static boolean spent(ProgressStep step, @Nullable Beat beat) {
    return beat != null && beat.attempts() >= StepPolicy.of(step).redReads();
  }

  private Optional<Decision> missed(ProgressStep step, @Nullable Beat last, long now) {
    if (last == null || now - last.lastAt() < beat.toMillis()) {
      return Optional.empty();
    }
    StepPolicy policy = StepPolicy.of(step);
    if (last.attempts() < policy.retries()) {
      return Optional.of(new Decision.Retry(step));
    }
    if (last.attempts() < policy.retries() + policy.restarts()) {
      return Optional.of(new Decision.Restart(step));
    }
    return policy.stop(now).map(move -> new Decision.Stop(step, move));
  }

  private Map<String, CheckerPhase> checkers(Configuration chart) {
    Map<String, String> plain = chart.toPlain();
    return composition.checkers().stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                id -> CheckerPhase.valueOf(Objects.requireNonNull(plain.get(id)))));
  }
}

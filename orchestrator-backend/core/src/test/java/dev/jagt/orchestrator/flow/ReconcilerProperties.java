package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.ChartRegions.CHECKS;
import static dev.jagt.orchestrator.flow.ChartRegions.DEPLOY;
import static dev.jagt.orchestrator.flow.ChartRegions.LIFECYCLE;
import static dev.jagt.orchestrator.flow.ChartRegions.REQUEST;
import static dev.jagt.orchestrator.flow.ChartRegions.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.jagt.orchestrator.flow.Reconciler.Decision;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.ChecksPhase;
import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.role.Arbiter;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.master.MasterMode;
import dev.jagt.orchestrator.task.master.MasterRight;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn.Holder;
import dev.jagt.statechart.Configuration;
import dev.jagt.statechart.Fired;
import dev.jagt.statechart.Macrostep;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.jqwik.api.Example;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.jspecify.annotations.Nullable;

class ReconcilerProperties {

  private static final TaskChart chart = new TaskChart(Composition.builtIn());

  private static final Arbiter.Profile ACTS =
      new Arbiter.Profile(MasterMode.ACT, Set.of(MasterRight.PLAN.id(), MasterRight.DEPLOY.id()));

  private static final Set<TaskStatus> P4 =
      EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.VERIFYING, TaskStatus.SHIPPING);

  @Example
  void everyReachableStateKeepsSafetyAndReachesItsGoalWithinTheBoundButTheKnownRows() {
    Model.Run run = new Model(chart, ACTS).explore();

    assertSoftly(soft -> {
      soft.assertThat(run.violations()).isEmpty();
      soft.assertThat(run.stalls(P4)).isEmpty();
      soft.assertThat(run.states()).isEqualTo(22_021);
      soft.assertThat(Model.bound()).isEqualTo(14);
    });
  }

  @Example
  void greenChecksInProgressStallWithNoMoverWithoutTheirKnownRow() {
    Model.Run run = new Model(chart, ACTS).explore();

    assertThat(run.stalls(EnumSet.of(TaskStatus.VERIFYING, TaskStatus.SHIPPING)))
        .anyMatch(stall -> stall.startsWith("IN_PROGRESS with green checks"));
  }

  static final class Model {

    private static final Duration BEAT = Duration.ofMinutes(1);
    private static final int SHOWN = 5;

    record World(
        Configuration chart, Pipeline checks, Pipeline deploy, Map<ProgressStep, Integer> beats) {

      TaskStatus status() {
        return chart.get(LIFECYCLE);
      }

      String described() {
        return status()
            + " with "
            + checks.name().toLowerCase(Locale.ROOT)
            + " checks, deploy "
            + deploy.name().toLowerCase(Locale.ROOT)
            + ", beats "
            + beats.entrySet().stream()
                .map(beat -> beat.getKey().getClass().getSimpleName() + "=" + beat.getValue())
                .sorted()
                .toList()
            + ", "
            + new TreeSet<>(
                chart.toPlain().entrySet().stream()
                    .map(region -> region.getKey() + "=" + region.getValue())
                    .toList());
      }
    }

    record Arrival(World from, String move) {}

    record Run(
        Map<World, @Nullable Arrival> reached,
        Map<World, World> silences,
        List<String> violations,
        Model model) {

      int states() {
        return reached.size();
      }

      List<String> stalls(Set<TaskStatus> known) {
        return model.stalls(this, known);
      }
    }

    private final TaskChart chart;
    private final Reconciler reconciler;

    Model(TaskChart chart, Arbiter.Profile profile) {
      this.chart = chart;
      this.reconciler = new Reconciler(chart.composition(), profile, BEAT);
    }

    Run explore() {
      Map<World, @Nullable Arrival> reached = new LinkedHashMap<>();
      Map<World, World> silences = new HashMap<>();
      List<String> violations = new ArrayList<>();
      Deque<World> frontier = new ArrayDeque<>();
      World start = start();
      reached.put(start, null);
      frontier.add(start);
      Run run = new Run(reached, silences, violations, this);
      while (!frontier.isEmpty()) {
        World at = frontier.poll();
        silences.put(at, tick(run, at, true, "silence"));
        enqueue(run, frontier, at, silences.get(at), "silence");
        for (Env move : moves(at)) {
          World moved = move.apply(this, run, at);
          enqueue(run, frontier, at, tick(run, moved, false, move.label()), move.label());
        }
      }
      return run;
    }

    private static void enqueue(Run run, Deque<World> frontier, World from, World to, String move) {
      if (!run.reached().containsKey(to)) {
        run.reached().put(to, new Arrival(from, move));
        frontier.add(to);
      }
    }

    private World start() {
      return reflight(
          new World(chart.chart().initial(), Pipeline.UNKNOWN, Pipeline.UNKNOWN, Map.of()));
    }

    private Reconciler.Stored stored(World world, long lastAt) {
      Map<ProgressStep, Beat> beats = new HashMap<>();
      world
          .beats()
          .forEach((step, attempts) -> beats.put(step, new Beat(step, 0, lastAt, attempts)));
      return new Reconciler.Stored(world.chart(), world.checks(), world.deploy(), beats);
    }

    private World reflight(World world) {
      Map<ProgressStep, Integer> beats = new HashMap<>();
      reconciler
          .inFlight(stored(world, 0))
          .forEach(step -> beats.put(step, world.beats().getOrDefault(step, 0)));
      boolean read = world.chart().get(CHECKS) == ChecksPhase.READ;
      boolean deployed = world.status() == TaskStatus.DEPLOYED;
      return new World(
          world.chart(),
          read ? world.checks() : Pipeline.UNKNOWN,
          deployed ? world.deploy() : Pipeline.UNKNOWN,
          Map.copyOf(beats));
    }

    private World tick(Run run, World at, boolean aging, String after) {
      long now = aging ? BEAT.toMillis() : 0;
      Decision decision = reconciler.decide(stored(at, 0), now);
      safety(run, at, decision, after);
      World next = reflight(decided(run, at, decision));
      restarted(run, at, next, decision, now);
      return next;
    }

    private World decided(Run run, World at, Decision decision) {
      return switch (decision) {
        case Decision.Stay stay -> at;
        case Decision.Land land -> stepped(run, at, land.move(), ActionOrigin.JAGT);
        case Decision.Retry retry -> retried(bumped(at, retry.step()), retry.step());
        case Decision.Restart restart -> bumped(at, restart.step());
        case Decision.Stop stop -> stepped(run, at, stop.move(), ActionOrigin.JAGT);
      };
    }

    private World retried(World at, ProgressStep step) {
      return step instanceof ProgressStep.Deploy
          ? new World(at.chart(), at.checks(), Pipeline.RUNNING, at.beats())
          : at;
    }

    private static World bumped(World at, ProgressStep step) {
      Map<ProgressStep, Integer> beats = new HashMap<>(at.beats());
      beats.merge(step, 1, Integer::sum);
      return new World(at.chart(), at.checks(), at.deploy(), Map.copyOf(beats));
    }

    private void safety(Run run, World at, Decision decision, String after) {
      Holder holder = reconciler.holder(stored(at, 0));
      boolean relays =
          (decision instanceof Decision.Retry retry && holdsTheTurn(retry.step()))
              || (decision instanceof Decision.Restart restart && holdsTheTurn(restart.step()));
      if (Holder.HUMAN.equals(holder) && relays) {
        violation(run, at, "S2 jagt moved where the human holds the turn: " + decision, after);
      }
      StopKind stop = at.chart().get(STOP);
      if (stop != StopKind.NONE && !(decision instanceof Decision.Stay) && !named(stop, decision)) {
        violation(run, at, "S5 a stopped task took more than its read: " + decision, after);
      }
    }

    private static boolean holdsTheTurn(ProgressStep step) {
      return step instanceof ProgressStep.SessionTurn || step instanceof ProgressStep.JudgeRound;
    }

    private static boolean named(StopKind stop, Decision decision) {
      ProgressStep step =
          switch (decision) {
            case Decision.Land land -> land.step();
            case Decision.Retry retry -> retry.step();
            case Decision.Restart restart -> restart.step();
            case Decision.Stop halt -> halt.step();
            case Decision.Stay stay -> new ProgressStep.SessionTurn();
          };
      return (stop == StopKind.HOST_READ && step instanceof ProgressStep.HostRead)
          || (stop == StopKind.DEPLOY_READ && step instanceof ProgressStep.Deploy);
    }

    private void restarted(Run run, World at, World next, Decision decision, long now) {
      if (decision instanceof Decision.Stay || decision instanceof Decision.Land) {
        return;
      }
      Decision again = reconciler.decide(stored(next, now), now);
      if (again.equals(decision)) {
        violation(run, at, "S4 a restart repeats " + decision, "restart");
      }
    }

    World stepped(Run run, World at, TaskMove move, @Nullable ActionOrigin origin) {
      Macrostep<TaskEffect> step = chart.chart().step(at.chart(), facts(at, origin), move);
      List<Fired> lifecycle =
          step.fired().stream().filter(fired -> fired.region().equals(LIFECYCLE.name())).toList();
      boolean moved = step.configuration().get(LIFECYCLE) != at.status();
      if (lifecycle.size() > 1 || (moved && lifecycle.size() != 1)) {
        violation(
            run, at, "S1 a status change is not one fired edge: " + lifecycle, move.toString());
      }
      return new World(step.configuration(), at.checks(), at.deploy(), at.beats());
    }

    private ChartFacts facts(World at, @Nullable ActionOrigin origin) {
      Facts none = Facts.defaults();
      RequestPhase request = at.chart().get(REQUEST);
      StopKind stop = at.chart().get(STOP);
      Facts read =
          none.withDelivery(
                  none.delivery()
                      .withHasReviewRequest(request != RequestPhase.NONE)
                      .withRequestMerged(request == RequestPhase.MERGED)
                      .withChecksGreen(green(at)))
              .withHold(
                  none.hold()
                      .withStopped(stop != StopKind.NONE && stop != StopKind.PAUSE)
                      .withPaused(stop == StopKind.PAUSE));
      return new ChartFacts(false, Map.of(), read, origin);
    }

    void violation(Run run, World at, String what, String move) {
      if (run.violations().size() < SHOWN) {
        run.violations().add(what + " after " + move + " at " + at.described() + path(run, at));
      }
    }

    private static String path(Run run, World at) {
      List<String> moves = new ArrayList<>();
      Arrival arrival = run.reached().get(at);
      while (arrival != null) {
        moves.addFirst(arrival.move());
        arrival = run.reached().get(arrival.from());
      }
      return " via " + moves;
    }

    private List<Env> moves(World at) {
      List<Env> moves = new ArrayList<>(Env.session());
      moves.addAll(Env.host(at));
      moves.addAll(presses(at));
      return moves;
    }

    private List<Env> presses(World at) {
      List<Env> presses = new ArrayList<>();
      for (TaskAction action : TaskAction.values()) {
        if (chart
            .chart()
            .takes(
                LIFECYCLE,
                at.chart(),
                facts(at, ActionOrigin.BOARD),
                new TaskMove.Offers(action))) {
          Arrays.stream(Outcome.Kind.values())
              .forEach(outcome ->
                  presses.add(
                      new Env.Press(new TaskMove.Ran(action, outcome), ActionOrigin.BOARD)));
        }
      }
      if (at.chart().get(STOP) != StopKind.NONE) {
        presses.add(new Env.Press(new TaskMove.Lifted(), ActionOrigin.BOARD));
      }
      return presses;
    }

    List<String> stalls(Run run, Set<TaskStatus> known) {
      Graph<World, DefaultEdge> waiting = new DefaultDirectedGraph<>(DefaultEdge.class);
      run.silences()
          .forEach((from, to) -> {
            if (!goal(from, known) && !goal(to, known)) {
              waiting.addVertex(from);
              waiting.addVertex(to);
              waiting.addEdge(from, to);
            }
          });
      Set<World> cycling = new CycleDetector<>(waiting).findCycles();
      List<String> stalls =
          cycling.stream()
              .map(world -> world.described() + path(run, world))
              .sorted()
              .limit(SHOWN)
              .collect(Collectors.toCollection(ArrayList::new));
      if (cycling.isEmpty()) {
        stalls.addAll(longest(run, waiting));
      }
      return stalls;
    }

    private List<String> longest(Run run, Graph<World, DefaultEdge> waiting) {
      Map<World, Integer> depth = new HashMap<>();
      new TopologicalOrderIterator<>(waiting)
          .forEachRemaining(world ->
              waiting
                  .outgoingEdgesOf(world)
                  .forEach(edge ->
                      depth.merge(
                          waiting.getEdgeTarget(edge),
                          depth.getOrDefault(world, 0) + 1,
                          Math::max)));
      return depth.entrySet().stream()
          .filter(entry -> entry.getValue() > bound())
          .map(entry -> "past the bound: " + entry.getKey().described() + path(run, entry.getKey()))
          .limit(SHOWN)
          .toList();
    }

    static int bound() {
      return ProgressStep.ALL.stream()
          .map(StepPolicy::of)
          .mapToInt(row -> row.retries() + row.restarts() + 1)
          .sum();
    }

    private boolean goal(World at, Set<TaskStatus> known) {
      if (landingOwed(at) && !known.contains(at.status())) {
        return false;
      }
      Holder holder = reconciler.holder(stored(at, 0));
      return at.status() == TaskStatus.DONE
          || (at.status() == TaskStatus.DEPLOYED
              && !at.beats().containsKey(new ProgressStep.Deploy()))
          || at.chart().get(STOP) != StopKind.NONE
          || at.chart().get(CHECKS) == ChecksPhase.UNREAD
          || Holder.ARBITER.equals(holder)
          || (Holder.HUMAN.equals(holder) && !presses(at).isEmpty())
          || offered(at, TaskAction.DEPLOY);
    }

    private boolean offered(World at, TaskAction action) {
      return chart
          .chart()
          .takes(LIFECYCLE, at.chart(), facts(at, ActionOrigin.BOARD), new TaskMove.Offers(action));
    }

    private static boolean green(World at) {
      return at.chart().get(CHECKS) == ChecksPhase.READ && at.checks() == Pipeline.GREEN;
    }

    private static boolean landingOwed(World at) {
      return at.chart().get(REQUEST) == RequestPhase.OPEN
          && at.chart().get(CHECKS) == ChecksPhase.READ
          && at.checks() == Pipeline.GREEN
          && EnumSet.of(
                  TaskStatus.NEW, TaskStatus.IN_PROGRESS, TaskStatus.VERIFYING, TaskStatus.SHIPPING)
              .contains(at.status());
    }

    interface Env {

      String label();

      World apply(Model model, Run run, World at);

      static List<Env> session() {
        List<Env> moves = new ArrayList<>();
        StatusSets.REPORTED.stream()
            .filter(status -> status != TaskStatus.CI_POLLING)
            .forEach(status -> moves.add(new Session(new TaskMove.Reported(status, false))));
        moves.add(new Ships());
        moves.add(new Session(new TaskMove.Reported(TaskStatus.REVIEW_PENDING, true)));
        moves.add(new Session(new TaskMove.Said(AgentReport.QUESTION)));
        moves.add(new Session(new TaskMove.Said(AgentReport.PLAIN)));
        moves.add(new Session(new TaskMove.TurnClosed()));
        return moves;
      }

      static List<Env> host(World at) {
        List<Env> moves = new ArrayList<>(deploys(at));
        if (at.chart().get(REQUEST) != RequestPhase.OPEN) {
          return moves;
        }
        moves.add(new Session(new TaskMove.ApprovalRead(true)));
        moves.add(new Session(new TaskMove.ApprovalRead(false)));
        moves.add(new Session(new TaskMove.ChecksUnread(new ChecksUnread(0, "down"))));
        EnumSet.of(Pipeline.RED, Pipeline.GREEN, Pipeline.RUNNING)
            .forEach(said -> moves.add(new ChecksSay(said)));
        if (at.checks() == Pipeline.RED) {
          moves.add(
              new Session(
                  new TaskMove.InfraArmed(InfraRetry.armed(0, "api", "u", "j", "r", 1, 2))));
        }
        return moves;
      }

      private static List<Env> deploys(World at) {
        if (at.status() != TaskStatus.DEPLOYED) {
          return List.of();
        }
        List<Env> moves = new ArrayList<>();
        if (at.chart().get(DEPLOY) == DeployPhase.NONE) {
          moves.add(new Session(new TaskMove.DeployStepped(new DeployStep.Opened(List.of()))));
        }
        EnumSet.of(Pipeline.RED, Pipeline.GREEN, Pipeline.RUNNING)
            .forEach(said -> moves.add(new DeploySays(said)));
        return moves;
      }

      final class Session implements Env {

        private final TaskMove move;

        Session(TaskMove move) {
          this.move = move;
        }

        @Override
        public String label() {
          return move.toString();
        }

        @Override
        public World apply(Model model, Run run, World at) {
          return model.stepped(run, at, move, ActionOrigin.MCP);
        }
      }

      record Ships() implements Env {

        @Override
        public String label() {
          return "reported CI_POLLING with its request";
        }

        @Override
        public World apply(Model model, Run run, World at) {
          World linked =
              model.stepped(
                  run,
                  at,
                  new TaskMove.RequestLinked(Map.of(), "u", false, false, true),
                  ActionOrigin.MCP);
          return model.stepped(
              run, linked, new TaskMove.Reported(TaskStatus.CI_POLLING, false), ActionOrigin.MCP);
        }
      }

      final class Press implements Env {

        private final TaskMove move;
        private final ActionOrigin origin;

        Press(TaskMove move, ActionOrigin origin) {
          this.move = move;
          this.origin = origin;
        }

        @Override
        public String label() {
          return "press " + move;
        }

        @Override
        public World apply(Model model, Run run, World at) {
          World shipped =
              move.equals(new TaskMove.Ran(TaskAction.SHIP, Outcome.Kind.OK))
                  ? model.stepped(
                      run,
                      at,
                      new TaskMove.RequestLinked(Map.of(), "u", false, false, true),
                      origin)
                  : at;
          World pressed = model.stepped(run, shipped, move, origin);
          boolean deploys =
              move instanceof TaskMove.Ran ran
                  && ran.action() == TaskAction.DEPLOY
                  && StatusSets.WAITING_FOR_A_DEPLOY.contains(at.status())
                  && pressed.status() != at.status();
          if (deploys && !green(at)) {
            model.violation(run, at, "S3 a deploy on facts not green", label());
          }
          return pressed;
        }
      }

      record ChecksSay(Pipeline said) implements Env {

        @Override
        public String label() {
          return "checks read " + said;
        }

        @Override
        public World apply(Model model, Run run, World at) {
          World read =
              model.stepped(
                  run,
                  at,
                  new TaskMove.ChecksRead(word(said), "tip", true),
                  ActionOrigin.CODE_HOST);
          return new World(read.chart(), said, read.deploy(), read.beats());
        }

        static String word(Pipeline said) {
          return switch (said) {
            case RED -> "failed";
            case GREEN -> "success";
            default -> "running";
          };
        }
      }

      record DeploySays(Pipeline said) implements Env {

        @Override
        public String label() {
          return "deploy pipeline read " + said;
        }

        @Override
        public World apply(Model model, Run run, World at) {
          return new World(at.chart(), at.checks(), said, at.beats());
        }
      }
    }
  }
}

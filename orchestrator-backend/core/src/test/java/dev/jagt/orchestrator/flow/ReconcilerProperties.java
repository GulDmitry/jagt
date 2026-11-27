package dev.jagt.orchestrator.flow;

import static dev.jagt.orchestrator.flow.ChartRegions.CHECKS;
import static dev.jagt.orchestrator.flow.ChartRegions.DEPLOY;
import static dev.jagt.orchestrator.flow.ChartRegions.LIFECYCLE;
import static dev.jagt.orchestrator.flow.ChartRegions.REQUEST;
import static dev.jagt.orchestrator.flow.ChartRegions.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.jagt.orchestrator.flow.Reconciler.Decision;
import dev.jagt.orchestrator.flow.ReconcilerProperties.FaultTable.Fault;
import dev.jagt.orchestrator.flow.ReconcilerProperties.FaultTable.Response;
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
import dev.jagt.orchestrator.task.TaskState;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
  void everyStateOfTwoReposKeepsSafetyAndTheFaultTableAndReachesItsGoalButGreenChecksInProgress() {
    Model.Run run = new Model(chart, ACTS).explore();

    assertSoftly(soft -> {
      soft.assertThat(run.violations()).isEmpty();
      soft.assertThat(run.stalls(P4)).isEmpty();
      soft.assertThat(run.offTable()).isEmpty();
      soft.assertThat(run.stalls(EnumSet.of(TaskStatus.VERIFYING, TaskStatus.SHIPPING)))
          .anyMatch(stall -> stall.startsWith("IN_PROGRESS with green checks"));
      soft.assertThat(run.states()).isEqualTo(12_730);
      soft.assertThat(Model.bound()).isEqualTo(11);
    });
  }

  @Example
  void deployRedTwiceIsConflictAndNoReachedWorldStopsUnreadOnRedDeploy() {
    Model.Run run = new Model(chart, ACTS).explore();

    Set<Model.World> reached = run.reached().keySet();
    assertSoftly(soft -> {
      soft.assertThat(reached)
          .noneMatch(world ->
              world.chart().get(STOP) == StopKind.DEPLOY_READ && world.deploy() == Pipeline.RED);
      soft.assertThat(reached)
          .anyMatch(world ->
              world.status() == TaskStatus.DEPLOYED
                  && world.deploy() == Pipeline.RUNNING
                  && world.deployReds() == 1);
      soft.assertThat(reached).anyMatch(world -> world.status() == TaskStatus.DEPLOY_CONFLICT);
    });
  }

  @Example
  void theFaultTableListsEveryFaultAtEveryStepOnce() {
    List<List<Object>> pairs =
        FaultTable.CELLS.stream().map(cell -> List.<Object>of(cell.fault(), cell.step())).toList();

    assertThat(pairs).doesNotHaveDuplicates().hasSize(72);
  }

  /**
   * Decision 0261's fault by step table, one cell each; {@link #admitted()} is what the model
   * admits per fault, the named stop ending any non-empty cell once its budget is spent (0260).
   */
  static final class FaultTable {

    enum Fault {
      SESSION_LIES(false),
      SESSION_SILENT(true),
      SESSION_DIES(false),
      CHECKS_RED(false),
      CHECKS_GREEN(false),
      CHECKS_FLAKY(false),
      CHECKS_SLOW(true),
      CHECKS_UNREAD(false),
      RESTART_EFFECT_UNRECORDED(false),
      TOKEN_STALE(false),
      READ_LANDS_LATE(false),
      JOB_THROWS(false);

      private final boolean missedBeat;

      Fault(boolean missedBeat) {
        this.missedBeat = missedBeat;
      }

      boolean missedBeat() {
        return missedBeat;
      }
    }

    enum Response {
      RETRY,
      RESTART,
      STOP
    }

    record Cell(Fault fault, Class<? extends ProgressStep> step, Set<Response> responses) {}

    static final List<Cell> CELLS =
        Stream.of(
                row(Fault.SESSION_LIES, "i", "i", "i", "i", "i", "i"),
                row(Fault.SESSION_SILENT, "r→R→S", "i", "i", "i", "i", "i"),
                row(Fault.SESSION_DIES, "R", "i", "i", "i", "i", "i"),
                row(Fault.CHECKS_RED, "i", "S", "S", "r", "i", "r→S"),
                row(Fault.CHECKS_GREEN, "i", "i", "i", "i", "i", "i"),
                row(Fault.CHECKS_FLAKY, "i", "r", "r", "r", "i", "r"),
                row(Fault.CHECKS_SLOW, "i", "r", "S", "r", "i", "S"),
                row(Fault.CHECKS_UNREAD, "i", "i", "r", "i", "i", "S"),
                row(Fault.RESTART_EFFECT_UNRECORDED, "i", "r", "r", "r", "r", "R"),
                row(Fault.TOKEN_STALE, "i", "i", "i", "i", "i", "i"),
                row(Fault.READ_LANDS_LATE, "i", "i", "i", "i", "i", "i"),
                row(Fault.JOB_THROWS, "i", "i", "i", "i", "i", "i"))
            .flatMap(List::stream)
            .toList();

    private FaultTable() {}

    static Map<Fault, Map<Class<? extends ProgressStep>, Set<Response>>> admitted() {
      return CELLS.stream()
          .filter(cell -> !cell.responses().isEmpty())
          .collect(
              Collectors.groupingBy(
                  Cell::fault,
                  () -> new EnumMap<>(Fault.class),
                  Collectors.toMap(Cell::step, FaultTable::ended)));
    }

    static Set<Response> admits(Fault fault, ProgressStep step) {
      return CELLS.stream()
          .filter(cell -> cell.fault() == fault && cell.step() == step.getClass())
          .map(FaultTable::ended)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("unlisted pair " + fault + " x " + step));
    }

    private static Set<Response> ended(Cell cell) {
      Set<Response> responses = EnumSet.noneOf(Response.class);
      responses.addAll(cell.responses());
      if (!responses.isEmpty()) {
        responses.add(Response.STOP);
      }
      return responses;
    }

    private static List<Cell> row(Fault fault, String... outcomes) {
      return IntStream.range(0, outcomes.length)
          .mapToObj(
              i -> new Cell(fault, ProgressStep.ALL.get(i).getClass(), responses(outcomes[i])))
          .toList();
    }

    private static Set<Response> responses(String outcome) {
      Set<Response> responses = EnumSet.noneOf(Response.class);
      outcome
          .chars()
          .forEach(letter -> {
            switch (letter) {
              case 'r' -> responses.add(Response.RETRY);
              case 'R' -> responses.add(Response.RESTART);
              case 'S' -> responses.add(Response.STOP);
              default -> {}
            }
          });
      return Set.copyOf(responses);
    }
  }

  static final class Model {

    private static final Duration BEAT = Duration.ofMinutes(1);
    private static final int SHOWN = 5;
    private static final int REPOS = 2;
    private static final String TASK = "ABC-42";
    private static final Generation UNDEPLOYED = new Generation(Generation.Kind.DEPLOY_COMMIT, "");

    /**
     * {@code checks} keeps a round's worst verdict alone: the next round reads both requests again.
     */
    record World(
        Configuration chart,
        Pipeline checks,
        List<Pipeline> targets,
        Map<ProgressStep, Integer> beats,
        int deployReds) {

      TaskStatus status() {
        return chart.get(LIFECYCLE);
      }

      Pipeline deploy() {
        return worst(targets.stream());
      }

      static Pipeline worst(Stream<Pipeline> said) {
        return said.min(Comparator.comparingInt(Pipeline::severity)).orElseThrow();
      }

      World withChart(Configuration next) {
        return new World(next, checks, targets, beats, deployReds);
      }

      World withChecks(Pipeline read) {
        return new World(chart, read, targets, beats, deployReds);
      }

      World withTargets(UnaryOperator<Pipeline> change) {
        return new World(
            chart, checks, targets.stream().map(change).sorted().toList(), beats, deployReds);
      }

      World withTarget(int index, Pipeline read) {
        List<Pipeline> next = new ArrayList<>(targets);
        next.set(index, read);
        return new World(chart, checks, next.stream().sorted().toList(), beats, deployReds);
      }

      String described() {
        return status()
            + " with "
            + checks.name().toLowerCase(Locale.ROOT)
            + " checks, deploy targets "
            + targets
            + ", beats "
            + beats.entrySet().stream()
                .map(beat -> beat.getKey().getClass().getSimpleName() + "=" + beat.getValue())
                .sorted()
                .toList()
            + ", deploy reds "
            + deployReds
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
        Set<String> offTable,
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
    private final Store store;
    private final FlowReports reports;

    Model(TaskChart chart, Arbiter.Profile profile) {
      this.chart = chart;
      this.reconciler = new Reconciler(chart.composition(), profile, BEAT);
      this.store = new Store(new TaskReducer(chart, stale -> {}, gap -> {}));
      this.reports =
          new FlowReports(
              store,
              Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
              new Checkers(chart),
              new FlowRules(chart));
    }

    static final class Store implements ReducingStore {

      private final TaskReducer reducer;
      private TaskState held = TaskState.builder(null, null, TaskStatus.NEW).build();

      Store(TaskReducer reducer) {
        this.reducer = reducer;
      }

      @Override
      public Optional<TaskState> task(String taskId) {
        return Optional.of(held);
      }

      @Override
      public String canonicalTaskId(String idOrAlias) {
        return idOrAlias;
      }

      @Override
      public boolean updateTask(String taskId, UnaryOperator<TaskState> update) {
        held = update.apply(held);
        return true;
      }

      @Override
      public boolean reduce(String taskId, BiFunction<TaskReducer, TaskState, TaskState> update) {
        held = update.apply(reducer, held);
        return true;
      }
    }

    Run explore() {
      Map<World, @Nullable Arrival> reached = new LinkedHashMap<>();
      Map<World, World> silences = new HashMap<>();
      Deque<World> frontier = new ArrayDeque<>();
      World start = start();
      reached.put(start, null);
      frontier.add(start);
      Run run = new Run(reached, silences, new ArrayList<>(), new TreeSet<>(), this);
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
      List<Pipeline> unread = List.of(Pipeline.UNKNOWN, Pipeline.UNKNOWN);
      return reflight(new World(chart.chart().initial(), Pipeline.UNKNOWN, unread, Map.of(), 0));
    }

    private Reconciler.Stored stored(World world, long lastAt) {
      Map<ProgressStep, Beat> beats = new HashMap<>();
      world
          .beats()
          .forEach((step, attempts) -> beats.put(step, new Beat(step, 0, lastAt, attempts, 0)));
      ProgressStep deploy = new ProgressStep.Deploy();
      Beat last = beats.getOrDefault(deploy, new Beat(deploy, 0, lastAt, 0, 0));
      if (world.deployReds() > 0) {
        beats.put(deploy, new Beat(deploy, 0, lastAt, last.attempts(), world.deployReds()));
      }
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
              world.targets(),
              Map.copyOf(beats),
              deployed ? world.deployReds() : 0)
          .withTargets(target -> deployed ? target : Pipeline.UNKNOWN);
    }

    private World tick(Run run, World at, boolean aging, String after) {
      long now = aging ? BEAT.toMillis() : 0;
      Decision decision = reconciler.decide(stored(at, 0), now);
      safety(run, at, decision, after);
      if (aging) {
        tabled(run, decision);
      }
      World next = reflight(decided(run, at, decision));
      restarted(run, at, next, decision, now);
      return next;
    }

    private static void tabled(Run run, Decision decision) {
      Optional<Response> response =
          switch (decision) {
            case Decision.Retry retry -> Optional.of(Response.RETRY);
            case Decision.Restart restart -> Optional.of(Response.RESTART);
            case Decision.Stop stop -> Optional.of(Response.STOP);
            default -> Optional.empty();
          };
      ProgressStep step = stepOf(decision);
      Set<Response> admitted = EnumSet.noneOf(Response.class);
      Arrays.stream(Fault.values())
          .filter(Fault::missedBeat)
          .forEach(fault -> admitted.addAll(FaultTable.admits(fault, step)));
      response
          .filter(taken -> !admitted.contains(taken))
          .ifPresent(taken ->
              run.offTable()
                  .add(step.getClass().getSimpleName() + " " + taken + " on a missed beat"));
    }

    private World decided(Run run, World at, Decision decision) {
      return switch (decision) {
        case Decision.Stay stay -> at;
        case Decision.Land land -> landed(run, at, land.move());
        case Decision.Rerun rerun -> rerun(at);
        case Decision.Retry retry -> retried(bumped(at, retry.step()), retry.step());
        case Decision.Restart restart -> bumped(at, restart.step());
        case Decision.Stop stop -> stepped(run, at, stop.move(), ActionOrigin.JAGT);
      };
    }

    private World landed(Run run, World at, TaskMove move) {
      store.held = TaskState.builder(null, null, at.status()).build();
      switch (move) {
        case TaskMove.Read read -> reports.read(TASK, read.concluded(), move.toString());
        case TaskMove.DeployRead red ->
            reports.readDeploy(
                TASK, UNDEPLOYED, red.redOnTheCode() ? "api" : null, move.toString());
        default -> throw new IllegalStateException("the tick lands no " + move);
      }
      TaskStatus door = store.held.standing().status();
      World stepped = stepped(run, at, move, ActionOrigin.JAGT);
      if (door == at.status() || door != stepped.status()) {
        violation(
            run,
            at,
            "LAND " + move + " lands " + door + " through door two, " + stepped.status() + " here",
            "tick");
      }
      return door == at.status() ? at : stepped;
    }

    private static World retried(World at, ProgressStep step) {
      return step instanceof ProgressStep.Deploy
          ? at.withTargets(target -> target == Pipeline.GREEN ? target : Pipeline.RUNNING)
          : at;
    }

    private static World rerun(World at) {
      return new World(at.chart(), at.checks(), at.targets(), at.beats(), at.deployReds() + 1)
          .withTargets(target -> target == Pipeline.GREEN ? target : Pipeline.RUNNING);
    }

    private static World bumped(World at, ProgressStep step) {
      Map<ProgressStep, Integer> beats = new HashMap<>(at.beats());
      beats.merge(step, 1, Integer::sum);
      return new World(at.chart(), at.checks(), at.targets(), Map.copyOf(beats), at.deployReds());
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

    private static ProgressStep stepOf(Decision decision) {
      return switch (decision) {
        case Decision.Land land -> land.step();
        case Decision.Rerun rerun -> rerun.step();
        case Decision.Retry retry -> retry.step();
        case Decision.Restart restart -> restart.step();
        case Decision.Stop halt -> halt.step();
        case Decision.Stay stay -> new ProgressStep.SessionTurn();
      };
    }

    private static boolean named(StopKind stop, Decision decision) {
      ProgressStep step = stepOf(decision);
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
      return at.withChart(step.configuration());
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
                      .withPaused(stop == StopKind.PAUSE))
              .withWorkspace(none.workspace().withSeveralRepos(true));
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
      List<Env> moves = new ArrayList<>(Env.hostFacts(at));
      Arrays.stream(Fault.values()).forEach(fault -> moves.addAll(Env.realising(fault, at)));
      moves.addAll(presses(at));
      return moves;
    }

    private List<Env> presses(World at) {
      List<Env> presses = new ArrayList<>();
      for (TaskAction action : TaskAction.values()) {
        if (offered(at, action)) {
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

      Set<Pipeline> READ = EnumSet.of(Pipeline.RED, Pipeline.RUNNING, Pipeline.GREEN);

      TaskMove LINKS_EVERY_REQUEST = new TaskMove.RequestLinked(Map.of(), "u", false, false, true);

      Generation ON_THE_HEAD = new Generation(Generation.Kind.HEAD, "tip");

      Generation PUSHED_SINCE = new Generation(Generation.Kind.HEAD, "pushed");

      String label();

      World apply(Model model, Run run, World at);

      /** The moves a fault takes in the model; a fault the model meets elsewhere takes none. */
      static List<Env> realising(Fault fault, World at) {
        return switch (fault) {
          case SESSION_LIES -> session();
          case CHECKS_RED -> red(at);
          case CHECKS_GREEN -> said(at, Pipeline.GREEN);
          case CHECKS_SLOW -> said(at, Pipeline.RUNNING);
          case CHECKS_UNREAD -> unread(at);
          case READ_LANDS_LATE -> late(at);
          case SESSION_SILENT,
              SESSION_DIES,
              CHECKS_FLAKY,
              RESTART_EFFECT_UNRECORDED,
              TOKEN_STALE,
              JOB_THROWS ->
              List.of();
        };
      }

      private static List<Env> session() {
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

      private static List<Env> red(World at) {
        List<Env> moves = new ArrayList<>(said(at, Pipeline.RED));
        if (at.chart().get(REQUEST) == RequestPhase.OPEN && at.checks() == Pipeline.RED) {
          moves.add(
              new Session(
                  new TaskMove.InfraArmed(InfraRetry.armed(0, "api", "u", "j", "r", 1, 2))));
        }
        return moves;
      }

      /** Every round whose worst verdict is {@code said}, and each target reading it. */
      private static List<Env> said(World at, Pipeline said) {
        List<Env> moves = new ArrayList<>();
        if (at.chart().get(REQUEST) == RequestPhase.OPEN) {
          READ.stream()
              .filter(other -> other.severity() >= said.severity())
              .forEach(other -> moves.add(new ChecksRound(said, other)));
        }
        if (at.status() == TaskStatus.DEPLOYED) {
          IntStream.range(0, REPOS).forEach(target -> moves.add(new DeploySays(target, said)));
        }
        return moves;
      }

      private static List<Env> unread(World at) {
        return at.chart().get(REQUEST) == RequestPhase.OPEN
            ? List.of(
                new Session(new TaskMove.ChecksUnread(ON_THE_HEAD, new ChecksUnread(0, "down"))))
            : List.of();
      }

      private static List<Env> late(World at) {
        return at.chart().get(REQUEST) == RequestPhase.OPEN
            ? Stream.<TaskMove.Observed>of(
                    new TaskMove.ChecksRead(ON_THE_HEAD, "success", "tip", true),
                    new TaskMove.ChecksRead(ON_THE_HEAD, "failed", "tip", true),
                    new TaskMove.ApprovalRead(ON_THE_HEAD, true),
                    new TaskMove.ChecksUnread(ON_THE_HEAD, new ChecksUnread(0, "down")))
                .<Env>map(LateRead::new)
                .toList()
            : List.of();
      }

      static List<Env> hostFacts(World at) {
        List<Env> moves = new ArrayList<>();
        if (at.status() == TaskStatus.DEPLOYED && at.chart().get(DEPLOY) == DeployPhase.NONE) {
          moves.add(new Session(new TaskMove.DeployStepped(new DeployStep.Opened(List.of()))));
        }
        if (at.chart().get(REQUEST) == RequestPhase.OPEN) {
          moves.add(new Session(new TaskMove.ApprovalRead(ON_THE_HEAD, true)));
          moves.add(new Session(new TaskMove.ApprovalRead(ON_THE_HEAD, false)));
        }
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
          return "reported CI_POLLING with every request";
        }

        @Override
        public World apply(Model model, Run run, World at) {
          World linked = model.stepped(run, at, LINKS_EVERY_REQUEST, ActionOrigin.MCP);
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
                  ? model.stepped(run, at, LINKS_EVERY_REQUEST, origin)
                  : at;
          World pressed = model.stepped(run, shipped, move, origin);
          boolean deploys =
              move instanceof TaskMove.Ran ran
                  && ran.action() == TaskAction.DEPLOY
                  && StatusSets.WAITING_FOR_A_DEPLOY.contains(at.status())
                  && pressed.status() != at.status();
          if (deploys && !green(at)) {
            model.violation(run, at, "S3 a deploy while a request is unready", label());
          }
          return pressed;
        }
      }

      record ChecksRound(Pipeline first, Pipeline second) implements Env {

        @Override
        public String label() {
          return "round reads " + first + " and " + second;
        }

        @Override
        public World apply(Model model, Run run, World at) {
          Pipeline worst = World.worst(Stream.of(first, second));
          return model
              .stepped(
                  run,
                  at,
                  new TaskMove.ChecksRead(ON_THE_HEAD, word(worst), "tip", true),
                  ActionOrigin.CODE_HOST)
              .withChecks(worst);
        }

        static String word(Pipeline said) {
          return switch (said) {
            case RED -> "failed";
            case GREEN -> "success";
            default -> "running";
          };
        }
      }

      record LateRead(TaskMove.Observed read) implements Env {

        @Override
        public String label() {
          return "late " + read;
        }

        @Override
        public World apply(Model model, Run run, World at) {
          World landed =
              read.readAt().landingOn(null, PUSHED_SINCE).isPresent()
                  ? at
                  : model.stepped(run, at, read, ActionOrigin.CODE_HOST);
          if (!landed.equals(at)) {
            model.violation(run, at, "S6 a read moved a newer state: " + read, label());
          }
          return landed;
        }
      }

      record DeploySays(int target, Pipeline said) implements Env {

        @Override
        public String label() {
          return "deploy target " + target + " read " + said;
        }

        @Override
        public World apply(Model model, Run run, World at) {
          return at.withTarget(target, said);
        }
      }
    }
  }
}

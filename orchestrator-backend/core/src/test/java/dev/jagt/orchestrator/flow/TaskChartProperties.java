package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Phases.Delivery;
import dev.jagt.orchestrator.flow.Phases.Gates;
import dev.jagt.orchestrator.flow.Phases.Progress;
import dev.jagt.orchestrator.flow.Phases.Watchers;
import dev.jagt.orchestrator.flow.TaskChartProperties.EdgeScanner.Edge;
import dev.jagt.orchestrator.flow.capability.Facts;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.ApprovalPhase;
import dev.jagt.orchestrator.flow.deploy.ChecksPhase;
import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.repo.GitPhase;
import dev.jagt.orchestrator.flow.repo.RepoPhase;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.role.CheckerRole;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.statechart.Configuration;
import dev.jagt.statechart.Fired;
import dev.jagt.statechart.Macrostep;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jspecify.annotations.Nullable;

class TaskChartProperties {

  private static final TaskChart chart = new TaskChart(Composition.builtIn());
  private static final EdgeScanner scanner = new EdgeScanner(chart);

  @Example
  void everyEdgeTheChartDrawsIsFiredByTheMovesTheChartKnows() {
    Set<String> fired = new Explorer(chart).fired();

    assertThat(fired).containsExactlyInAnyOrderElementsOf(scanner.declared());
  }

  @Example
  void everyKindOfMoveTheChartTakesHasAnInstanceToDriveIt() {
    Set<Class<?>> built =
        new Explorer(chart).moves().stream().map(Object::getClass).collect(Collectors.toSet());

    assertThat(MoveUniverse.leaves(TaskMove.class)).isSubsetOf(built);
  }

  @Property(generation = GenerationMode.EXHAUSTIVE)
  void everyLifecycleEdgeLandsWhereTheChartNamesIt(@ForAll("edges") Edge edge) {
    Configuration after =
        chart.step(scanner.phasesAt(edge), EdgeScanner.moveNamed(edge.label())).configuration();

    assertThat(after.toPlain()).containsEntry("lifecycle", edge.to().name());
  }

  @Property
  void walkOverEdgesLeavingEachStatusLandsOnEachEdgesTarget(
      @ForAll("choices") List<Integer> choices) {
    Configuration standing = chart.chart().initial();
    for (int choice : choices) {
      TaskStatus at =
          TaskStatus.valueOf(Objects.requireNonNull(standing.toPlain().get("lifecycle")));
      List<Edge> leaving =
          scanner.lifecycleEdges().stream()
              .filter(edge -> edge.from() == at)
              .filter(edge -> !edge.label().equals("acceptance round declined"))
              .toList();
      Edge taken = leaving.get(choice % leaving.size());

      standing =
          chart
              .chart()
              .step(standing, Phases.at(at).facts(), EdgeScanner.moveNamed(taken.label()))
              .configuration();

      assertThat(standing.toPlain()).containsEntry("lifecycle", taken.to().name());
    }
  }

  @Provide
  Arbitrary<Edge> edges() {
    return Arbitraries.of(scanner.lifecycleEdges());
  }

  @Provide
  Arbitrary<List<Integer>> choices() {
    return Arbitraries.integers().between(0, 1_000).list().ofMinSize(1).ofMaxSize(12);
  }

  static final class EdgeScanner {

    private static final Map<String, TaskMove> LITERAL_MOVES =
        Map.of(
            "verified", new TaskMove.Verified(true),
            "verification failed", new TaskMove.Verified(false),
            "deploy read red", new TaskMove.DeployRead(true),
            "deploy read green", new TaskMove.DeployRead(false),
            "conflicted repository left",
                new TaskMove.RepoLeft("api", RequestPhase.NONE, TaskMove.ConflictDrop.LANDS),
            "conflicted repository left, nothing landed",
                new TaskMove.RepoLeft("api", RequestPhase.NONE, TaskMove.ConflictDrop.RETURNS),
            "acceptance round declined",
                new TaskMove.Checked.RoundDeclined(
                    "acceptance", AgentReport.NO_CHANGES, TaskStatus.IN_PROGRESS));

    record Edge(TaskStatus from, String label, TaskStatus to) {}

    private final TaskChart chart;
    private final List<Edge> lifecycleEdges;

    EdgeScanner(TaskChart chart) {
      this.chart = chart;
      this.lifecycleEdges = scanLifecycle();
    }

    List<Edge> lifecycleEdges() {
      return lifecycleEdges;
    }

    Set<String> regionsDrawn() {
      Matcher region =
          Pattern.compile("(?m)^    state (\\w+) \\{$").matcher(chart.chart().render());
      Set<String> names = new TreeSet<>();
      while (region.find()) {
        names.add(region.group(1));
      }
      return names;
    }

    Set<String> declared() {
      String render = chart.chart().render();
      Set<String> regions = regionsDrawn();
      Map<String, String> aliases = aliasesIn(render);
      Map<String, Set<String>> states = statesIn(render, regions);
      Map<String, List<String>> branches = branchesIn(render);
      return Pattern.compile("(?m)^ {8}(\\w+) --> (\\w+) : (.+)$")
          .matcher(render)
          .results()
          .filter(arrow -> !arrow.group(3).matches("\\[(?:branch|otherwise)\\]"))
          .flatMap(arrow -> expanded(arrow, regions, states, aliases, branches).stream())
          .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Map<String, String> aliasesIn(String render) {
      return Pattern.compile("state \"(.+)\" as (\\w+_from_\\d+)")
          .matcher(render)
          .results()
          .collect(Collectors.toMap(alias -> alias.group(2), alias -> alias.group(1)));
    }

    private static Map<String, Set<String>> statesIn(String render, Set<String> regions) {
      Map<String, Set<String>> states = new HashMap<>();
      Pattern.compile("(?m)^\\s+state (?:\"(\\w+)\" as (\\w+)|(\\w+) (?:\\{|<<choice>>))")
          .matcher(render)
          .results()
          .map(node -> node.group(2) != null ? node.group(2) : node.group(3))
          .filter(id -> !id.matches(".*_from_\\d+"))
          .forEach(id ->
              regionOf(regions, id)
                  .ifPresent(owner ->
                      states
                          .computeIfAbsent(owner, key -> new TreeSet<>())
                          .add(id.substring(owner.length() + 1))));
      return states;
    }

    private static Map<String, List<String>> branchesIn(String render) {
      return Pattern.compile("(?m)^ {8}(\\w+) --> (\\w+) : \\[(?:branch|otherwise)\\]$")
          .matcher(render)
          .results()
          .collect(
              Collectors.groupingBy(
                  branch -> branch.group(1),
                  Collectors.mapping(branch -> branch.group(2), Collectors.toList())));
    }

    private static List<String> expanded(
        MatchResult arrow,
        Set<String> regions,
        Map<String, Set<String>> states,
        Map<String, String> aliases,
        Map<String, List<String>> branches) {
      String label = arrow.group(3).replaceAll(" \\[\\w+ history\\]$", "");
      String source = arrow.group(1);
      String region =
          aliases.containsKey(source)
              ? source.replaceAll("_from_\\d+$", "")
              : regionOf(regions, source).orElseThrow();
      boolean loops = source.equals(arrow.group(2));
      List<String> edges = new ArrayList<>();
      for (String from : sourcesOf(states, aliases, region, source)) {
        String target = loops ? from : arrow.group(2).substring(region.length() + 1);
        resolved(branches, region, target)
            .forEach(leaf -> edges.add(String.join("|", region, from, leaf, label)));
      }
      return edges;
    }

    private static Optional<String> regionOf(Set<String> regions, String id) {
      return regions.stream()
          .filter(region -> id.startsWith(region + "_"))
          .max(Comparator.comparingInt(String::length));
    }

    private static List<String> resolved(
        Map<String, List<String>> branches, String region, String target) {
      List<String> via = branches.get(region + "_" + target);
      if (via == null) {
        return List.of(target);
      }
      return via.stream()
          .flatMap(next -> resolved(branches, region, next.substring(region.length() + 1)).stream())
          .toList();
    }

    private static Set<String> sourcesOf(
        Map<String, Set<String>> statesOf, Map<String, String> aliases, String region, String id) {
      String text = aliases.get(id);
      if (text == null) {
        return Set.of(id.substring(region.length() + 1));
      }
      Set<String> all = Objects.requireNonNull(statesOf.get(region));
      if (text.equals("all")) {
        return all;
      }
      if (text.startsWith("all but ")) {
        Set<String> left = new TreeSet<>(all);
        left.removeAll(Arrays.asList(text.substring("all but ".length()).split(", ")));
        return left;
      }
      return new TreeSet<>(Arrays.asList(text.split(", ")));
    }

    Phases phasesAt(Edge edge) {
      return edge.label().equals("acceptance round declined")
          ? new Phases(
              new Progress(
                  edge.from(),
                  StopKind.NONE,
                  PlanGate.OPEN,
                  AgentReport.PLAIN,
                  TurnKind.OPEN,
                  SessionPhase.NONE),
              new Delivery(DeployPhase.NONE, GitPhase.UNREAD, RepoPhase.NONE, RequestPhase.NONE),
              new Gates(ChecksPhase.NONE, ApprovalPhase.UNREAD, InfraPhase.NONE),
              new Watchers(
                  Map.of("acceptance", CheckerPhase.ROUND_OPEN),
                  Map.of(),
                  MasterPhase.STANDS_IN,
                  false))
          : Phases.at(edge.from());
    }

    static TaskMove moveNamed(String label) {
      TaskMove literal = LITERAL_MOVES.get(label);
      if (literal != null) {
        return literal;
      }
      if (label.startsWith("offers ")) {
        return new TaskMove.Offers(actionNamed(label.substring("offers ".length())));
      }
      if (label.startsWith("read ")) {
        return new TaskMove.Read(TaskStatus.valueOf(label.substring("read ".length())));
      }
      if (label.startsWith("report ")) {
        String reported = label.substring("report ".length());
        boolean owed = reported.endsWith(" owing verification");
        return new TaskMove.Reported(
            TaskStatus.valueOf(reported.replace(" owing verification", "")), owed);
      }
      int space = label.indexOf(' ');
      return new TaskMove.Ran(
          actionNamed(label.substring(0, space)), Outcome.Kind.valueOf(label.substring(space + 1)));
    }

    private static TaskAction actionNamed(String id) {
      return Arrays.stream(TaskAction.values())
          .filter(action -> action.id().equals(id))
          .findFirst()
          .orElseThrow();
    }

    private static Set<TaskStatus> statusesOf(Map<String, String> sets, String name) {
      String text = sets.get(name);
      if (text == null) {
        return EnumSet.of(TaskStatus.valueOf(name.substring("lifecycle_".length())));
      }
      if (text.equals("all")) {
        return EnumSet.allOf(TaskStatus.class);
      }
      Set<TaskStatus> listed =
          Arrays.stream(text.replace("all but ", "").split(", "))
              .map(TaskStatus::valueOf)
              .collect(Collectors.toCollection(() -> EnumSet.noneOf(TaskStatus.class)));
      return text.startsWith("all but ") ? EnumSet.complementOf(EnumSet.copyOf(listed)) : listed;
    }

    private List<Edge> scanLifecycle() {
      String region = chart.chart().render().split("    --\n", 0)[0];
      Map<String, String> sets = new HashMap<>();
      Matcher alias = Pattern.compile("state \"(.+)\" as (lifecycle_from_\\d+)").matcher(region);
      while (alias.find()) {
        sets.put(alias.group(2), alias.group(1));
      }
      List<Edge> edges = new ArrayList<>();
      Matcher arrow =
          Pattern.compile("(lifecycle_\\S+) --> (lifecycle_\\S+) : (.+)").matcher(region);
      while (arrow.find()) {
        boolean loop = arrow.group(1).equals(arrow.group(2));
        for (TaskStatus from : statusesOf(sets, arrow.group(1))) {
          TaskStatus to = loop ? from : statusesOf(sets, arrow.group(2)).iterator().next();
          edges.add(new Edge(from, arrow.group(3), to));
        }
      }
      return edges.stream().distinct().toList();
    }
  }

  static final class MoveUniverse {

    private static final int PRODUCT_LIMIT = 120;

    private static final Map<Class<?>, List<Object>> SCALARS =
        Map.of(
            boolean.class, List.of(false, true),
            Boolean.class, List.of(false, true),
            long.class, List.of(1L),
            Long.class, List.of(1L),
            int.class, List.of(1, 2),
            Integer.class, List.of(1, 2),
            Map.class, List.of(Map.of()),
            List.class, List.of(List.of()));

    private final List<String> roles;
    private final List<String> steps;

    MoveUniverse(List<String> roles, List<String> steps) {
      this.roles = roles;
      this.steps = steps;
    }

    List<TaskMove> moves() {
      return leaves(TaskMove.class).stream()
          .flatMap(leaf -> candidates(leaf, false, "").stream())
          .map(TaskMove.class::cast)
          .distinct()
          .toList();
    }

    static Set<Class<?>> leaves(Class<?> type) {
      if (!type.isSealed()) {
        return Set.of(type);
      }
      return Arrays.stream(type.getPermittedSubclasses())
          .flatMap(permitted -> leaves(permitted).stream())
          .collect(Collectors.toSet());
    }

    private List<Object> candidates(Class<?> type, boolean nullable, String name) {
      List<Object> values = new ArrayList<>();
      if (nullable) {
        values.add(null);
      }
      values.addAll(nonNull(type, name));
      return values;
    }

    private List<Object> nonNull(Class<?> type, String name) {
      if (type == String.class) {
        return new ArrayList<>(strings(name));
      }
      if (type.isEnum()) {
        return Arrays.asList(type.getEnumConstants());
      }
      if (type.isSealed()) {
        return Arrays.stream(type.getPermittedSubclasses())
            .flatMap(permitted -> candidates(permitted, false, name).stream())
            .toList();
      }
      if (type.isRecord()) {
        return records(type);
      }
      return SCALARS.getOrDefault(type, List.of());
    }

    private List<String> strings(String name) {
      return switch (name) {
        case "role" -> roles;
        case "step" -> steps;
        case "digest" -> List.of("x", "");
        default -> List.of("x");
      };
    }

    private List<Object> records(Class<?> type) {
      RecordComponent[] components = type.getRecordComponents();
      List<List<Object>> values = new ArrayList<>();
      for (RecordComponent component : components) {
        values.add(
            candidates(
                component.getType(),
                component.getAnnotatedType().isAnnotationPresent(Nullable.class),
                component.getName()));
      }
      if (values.stream().anyMatch(List::isEmpty)) {
        return List.of();
      }
      Class<?>[] types =
          Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
      return rows(values).stream()
          .flatMap(row -> construct(type, types, row).stream())
          .distinct()
          .toList();
    }

    private static List<@Nullable Object[]> rows(List<List<Object>> values) {
      long product = values.stream().mapToLong(List::size).reduce(1, (a, b) -> a * b);
      List<@Nullable Object[]> rows = new ArrayList<>();
      if (product <= PRODUCT_LIMIT) {
        rows.add(new Object[0]);
        for (List<Object> column : values) {
          List<@Nullable Object[]> widened = new ArrayList<>();
          rows.forEach(row -> column.forEach(value -> widened.add(with(row, value))));
          rows = widened;
        }
        return rows;
      }
      @Nullable Object[] base = values.stream().map(MoveUniverse::preferred).toArray();
      rows.add(base);
      for (int at = 0; at < base.length; at++) {
        for (Object value : values.get(at)) {
          @Nullable Object[] varied = base.clone();
          varied[at] = value;
          rows.add(varied);
        }
      }
      return rows;
    }

    private static @Nullable Object[] with(@Nullable Object[] row, @Nullable Object value) {
      @Nullable Object[] longer = Arrays.copyOf(row, row.length + 1);
      longer[row.length] = value;
      return longer;
    }

    private static @Nullable Object preferred(List<Object> values) {
      return values.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static Optional<Object> construct(
        Class<?> type, Class<?>[] types, @Nullable Object[] row) {
      try {
        Constructor<?> constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return Optional.of(constructor.newInstance(row));
      } catch (ReflectiveOperationException refused) {
        return Optional.empty();
      }
    }
  }

  static final class Explorer {

    private static final int CEILING = 2_000;

    private record Key(Map<String, String> states, Map<String, String> history) {

      static Key of(Configuration configuration) {
        return new Key(configuration.toPlain(), configuration.history());
      }
    }

    private final TaskChart chart;

    Explorer(TaskChart chart) {
      this.chart = chart;
    }

    Set<String> fired() {
      List<Map.Entry<TaskMove, ChartFacts>> presses = presses();
      List<Map.Entry<TaskMove, ChartFacts>> afterTheStart =
          presses.stream().filter(press -> !offers(press)).toList();
      Set<String> fired = new TreeSet<>();
      Set<String> placed = new HashSet<>();
      Set<Key> seen = new HashSet<>();
      Deque<Configuration> frontier = new ArrayDeque<>();
      starts().stream().filter(start -> seen.add(Key.of(start))).forEach(frontier::add);
      Set<Key> startKeys = new HashSet<>(seen);
      while (!frontier.isEmpty()) {
        Configuration at = frontier.poll();
        List<Map.Entry<TaskMove, ChartFacts>> tried =
            startKeys.contains(Key.of(at)) ? presses : afterTheStart;
        List<Macrostep<TaskEffect>> steps =
            tried.parallelStream()
                .map(press -> chart.chart().step(at, press.getValue(), press.getKey()))
                .toList();
        for (Macrostep<TaskEffect> step : steps) {
          if (learns(at, step, fired, placed) && seen.add(Key.of(step.configuration()))) {
            frontier.add(step.configuration());
          }
        }
      }
      assertThat(seen).hasSizeLessThan(CEILING);
      return fired;
    }

    private static boolean learns(
        Configuration at, Macrostep<TaskEffect> step, Set<String> fired, Set<String> placed) {
      boolean novel = false;
      for (Fired f : step.fired()) {
        novel |= fired.add(label(f));
      }
      return placed.addAll(placements(at, step.configuration())) || novel;
    }

    private static boolean offers(Map.Entry<TaskMove, ChartFacts> press) {
      return press.getKey() instanceof TaskMove.Offers;
    }

    private static Set<String> placements(Configuration before, Configuration after) {
      Map<String, String> was = before.toPlain();
      Map<String, String> plain = after.toPlain();
      String lifecycle = Objects.requireNonNull(plain.get("lifecycle"));
      String turn = Objects.requireNonNull(plain.get("turn"));
      Set<String> placed = new HashSet<>();
      plain.forEach((region, state) -> {
        if (!state.equals(was.get(region))) {
          placed.add(String.join("|", lifecycle, region, state));
          placed.add(String.join("|", lifecycle, turn, region, state));
        }
      });
      return placed;
    }

    private static String label(Fired fired) {
      return String.join("|", fired.region(), fired.source(), fired.target(), fired.trigger());
    }

    private List<Configuration> starts() {
      Configuration initial = chart.chart().initial();
      List<Configuration> starts = new ArrayList<>(List.of(initial));
      for (TaskStatus status : TaskStatus.values()) {
        Map<String, String> plain = new HashMap<>(initial.toPlain());
        plain.put("lifecycle", status.name());
        starts.add(chart.chart().configuration(plain));
      }
      return starts;
    }

    List<TaskMove> moves() {
      List<CheckerRole> checkers =
          chart.composition().roles().stream()
              .filter(CheckerRegion.class::isInstance)
              .map(role -> ((CheckerRegion) role).role())
              .toList();
      return new MoveUniverse(
              checkers.stream().map(CheckerRole::id).toList(),
              checkers.stream().flatMap(role -> role.readSteps().stream()).toList())
          .moves();
    }

    private List<Map.Entry<TaskMove, ChartFacts>> presses() {
      List<Map.Entry<TaskMove, ChartFacts>> presses = new ArrayList<>();
      List<ChartFacts> surroundings = surroundings();
      for (TaskMove move : moves()) {
        List<ChartFacts> facts = move instanceof TaskMove.Offers ? offered() : surroundings;
        facts.forEach(around -> presses.add(Map.entry(move, around)));
      }
      return presses;
    }

    private List<ChartFacts> surroundings() {
      List<ChartFacts> facts = new ArrayList<>();
      for (boolean stamped : List.of(false, true)) {
        facts.add(around(stamped, CheckerPhase.NONE));
      }
      facts.add(around(false, CheckerPhase.ROUND_OPEN));
      facts.add(around(false, CheckerPhase.PASSED));
      return facts;
    }

    private ChartFacts around(boolean stamped, CheckerPhase beneath) {
      Map<String, CheckerPhase> under = new HashMap<>();
      chart.installedCheckers().forEach(id -> under.put(id, beneath));
      return new ChartFacts(stamped, under, Facts.defaults(), ActionOrigin.BOARD);
    }

    private static List<ChartFacts> offered() {
      Facts none = Facts.defaults();
      Facts every =
          new Facts(
              new Facts.Delivery(true, true, true, true, true, () -> true),
              new Facts.Hold(true, true, true, true, () -> true),
              new Facts.Workspace(true, true, true, Optional::empty, Optional::empty),
              new Facts.Reach(true, true, false, null));
      List<Facts> variants =
          List.of(
              none,
              every,
              none.withDelivery(none.delivery().withHasReviewRequest(true)),
              none.withDelivery(none.delivery().withRequestMerged(true)),
              none.withDelivery(none.delivery().withLiveDeploy(true)),
              none.withDelivery(none.delivery().withRevertConflict(true)),
              none.withDelivery(none.delivery().withShippedWithoutRequest(true)),
              none.withDelivery(none.delivery().withMergedNow(() -> true)),
              none.withHold(none.hold().withStopped(true)),
              none.withHold(none.hold().withTierAbove(true)),
              none.withHold(none.hold().withPlanClosed(true)),
              none.withHold(none.hold().withAgentLive(() -> true)),
              none.withWorkspace(none.workspace().withGitStateChanged(true)),
              none.withWorkspace(none.workspace().withRepoRequested(true)),
              none.withWorkspace(none.workspace().withSeveralRepos(true)),
              none.withReach(none.reach().withScenarios(true)),
              none.withReach(none.reach().withShellEscape(true)));
      return variants.stream()
          .map(facts -> new ChartFacts(false, Map.of(), facts, ActionOrigin.BOARD))
          .toList();
    }
  }
}

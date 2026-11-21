package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Phases.Delivery;
import dev.jagt.orchestrator.flow.Phases.Gates;
import dev.jagt.orchestrator.flow.Phases.Progress;
import dev.jagt.orchestrator.flow.Phases.Watchers;
import dev.jagt.orchestrator.flow.TaskChartProperties.EdgeScanner.Edge;
import dev.jagt.orchestrator.flow.capability.Outcome;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.deploy.ApprovalPhase;
import dev.jagt.orchestrator.flow.deploy.ChecksPhase;
import dev.jagt.orchestrator.flow.deploy.DeployPhase;
import dev.jagt.orchestrator.flow.deploy.InfraPhase;
import dev.jagt.orchestrator.flow.repo.GitPhase;
import dev.jagt.orchestrator.flow.repo.RepoPhase;
import dev.jagt.orchestrator.flow.repo.RequestPhase;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.StopKind;
import dev.jagt.orchestrator.flow.session.TurnKind;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.statechart.Configuration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TaskChartProperties {

  private static final TaskChart chart = new TaskChart(Composition.builtIn());
  private static final EdgeScanner scanner = new EdgeScanner(chart);

  @Example
  void everyRegionTheChartDrawsIsDrivenOrNamedUndriven() {
    Set<String> others = new TreeSet<>(scanner.regionsDrawn());
    others.remove("lifecycle");

    assertThat(others).isEqualTo(EdgeScanner.UNDRIVEN_REGIONS.keySet());
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

    static final Map<String, String> UNDRIVEN_REGIONS =
        Stream.of(
                "acceptance",
                "approval",
                "checks",
                "deploy",
                "git",
                "infra",
                "master",
                "plan",
                "repo",
                "report",
                "request",
                "session",
                "stop",
                "turn")
            .collect(
                Collectors.toMap(
                    region -> region,
                    region ->
                        "its edges read task data beyond the lifecycle status;"
                            + " no move table for them exists yet"));

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
}

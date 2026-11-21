package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class Mermaid {

  private Mermaid() {}

  static <E, X, F> String render(
      List<Region<E, F>> regions, List<Datum<X>> data, List<Transition<E, F>> transitions) {
    List<String> lines = new ArrayList<>(List.of("stateDiagram-v2"));
    Map<String, Region<E, F>> byName = new LinkedHashMap<>();
    regions.forEach(region -> byName.put(region.name(), region));
    Set<String> nested = new HashSet<>();
    regions.forEach(region -> region.tree().parallels().values().forEach(nested::addAll));
    List<Region<E, F>> top = regions.stream().filter(r -> !nested.contains(r.name())).toList();
    for (int index = 0; index < top.size(); index++) {
      if (index > 0) {
        lines.add("    --");
      }
      lines.addAll(block(top.get(index), transitions, byName));
    }
    data.forEach(datum -> lines.add("    %% data " + datum.name()));
    return String.join("\n", lines) + "\n";
  }

  private static <E, F> List<String> block(
      Region<E, F> region, List<Transition<E, F>> transitions, Map<String, Region<E, F>> byName) {
    List<String> lines = new ArrayList<>();
    lines.add("    state " + region.name() + " {");
    region.states().stream()
        .filter(state -> region.tree().parent(state).isEmpty())
        .forEach(state -> lines.addAll(stateLines(region, state, "        ", transitions, byName)));
    lines.add("        [*] --> " + id(region, region.initial()));
    List<Transition<E, F>> own =
        transitions.stream().filter(t -> t.region().equals(region.name())).toList();
    Set<Integer> drawn = new HashSet<>();
    for (Transition<E, F> t : own) {
      List<Transition<E, F>> sharing =
          own.stream().filter(other -> t.group() > 0 && other.group() == t.group()).toList();
      if (sharing.size() < 2) {
        lines.add(
            "        "
                + id(region, t.source())
                + " --> "
                + id(region, t.target())
                + " : "
                + label(t));
      } else if (drawn.add(t.group())) {
        String source = region.name() + "_from_" + t.group();
        boolean loops = sharing.stream().allMatch(m -> m.source() == m.target());
        lines.add("        state \"" + sources(region, sharing) + "\" as " + source);
        lines.add(
            "        "
                + source
                + " --> "
                + (loops ? source : id(region, t.target()))
                + " : "
                + label(t));
      }
    }
    region.states().stream()
        .filter(region.tree()::isChoice)
        .forEach(state -> lines.addAll(branchLines(region, state, region.tree().choice(state))));
    region.states().stream()
        .filter(state -> region.finals().contains(state) && region.tree().parent(state).isEmpty())
        .forEach(state -> lines.add("        " + id(region, state) + " --> [*]"));
    lines.add("    }");
    return lines;
  }

  private static <E, F> List<String> parallelLines(
      Region<E, F> region,
      Enum<?> state,
      List<Transition<E, F>> transitions,
      Map<String, Region<E, F>> byName) {
    List<String> lines = new ArrayList<>(List.of("        state " + id(region, state) + " {"));
    List<String> owned = region.tree().regionsOf(state);
    for (int index = 0; index < owned.size(); index++) {
      if (index > 0) {
        lines.add("        --");
      }
      lines.addAll(
          block(Objects.requireNonNull(byName.get(owned.get(index))), transitions, byName));
    }
    lines.add("        }");
    lines.add("        " + id(region, state) + " : " + state.name());
    return lines;
  }

  private static <E, F> List<String> branchLines(
      Region<E, F> region, Enum<?> choice, Choice decided) {
    List<String> lines = new ArrayList<>();
    decided
        .branches()
        .forEach(branch ->
            lines.add(
                "        "
                    + id(region, choice)
                    + " --> "
                    + id(region, branch.target())
                    + " : [branch]"));
    decided
        .otherwise()
        .ifPresent(target ->
            lines.add(
                "        " + id(region, choice) + " --> " + id(region, target) + " : [otherwise]"));
    return lines;
  }

  private static <E, F> List<String> stateLines(
      Region<E, F> region,
      Enum<?> state,
      String indent,
      List<Transition<E, F>> transitions,
      Map<String, Region<E, F>> byName) {
    if (!region.tree().regionsOf(state).isEmpty()) {
      return parallelLines(region, state, transitions, byName);
    }
    if (region.tree().isChoice(state)) {
      return List.of(indent + "state " + id(region, state) + " <<choice>>");
    }
    if (!region.tree().isCompound(state)) {
      return List.of(indent + "state \"" + state.name() + "\" as " + id(region, state));
    }
    String inner = indent + "    ";
    List<String> lines = new ArrayList<>(List.of(indent + "state " + id(region, state) + " {"));
    region
        .tree()
        .kids(state)
        .forEach(kid -> lines.addAll(stateLines(region, kid, inner, transitions, byName)));
    lines.add(inner + "[*] --> " + id(region, region.tree().initial(state)));
    region.tree().kids(state).stream()
        .filter(region.finals()::contains)
        .forEach(kid -> lines.add(inner + id(region, kid) + " --> [*]"));
    lines.add(indent + "}");
    lines.add(indent + id(region, state) + " : " + state.name());
    return lines;
  }

  private static <E, F> String sources(Region<E, F> region, List<Transition<E, F>> sharing) {
    Set<Enum<?>> from = sharing.stream().map(Transition::source).collect(Collectors.toSet());
    if (from.size() * 2 <= region.states().size()) {
      return sharing.stream().map(t -> t.source().name()).collect(Collectors.joining(", "));
    }
    String left =
        region.states().stream()
            .filter(state -> !from.contains(state))
            .map(Enum::name)
            .collect(Collectors.joining(", "));
    return left.isEmpty() ? "all" : "all but " + left;
  }

  private static String label(Transition<?, ?> t) {
    return t.goal()
        .via()
        .map(kind -> t.trigger().label() + " [" + kind + " history]")
        .orElse(t.trigger().label());
  }

  private static String id(Region<?, ?> region, Enum<?> state) {
    return region.name() + "_" + state.name();
  }
}

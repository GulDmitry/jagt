package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class Mermaid {

  private Mermaid() {}

  static <E, X, F> String render(
      List<Stored<F>> stored, List<Derived<X>> derived, List<Edge<E, F>> transitions) {
    List<String> lines = new ArrayList<>(List.of("stateDiagram-v2"));
    for (int index = 0; index < stored.size(); index++) {
      if (index > 0) {
        lines.add("    --");
      }
      lines.addAll(block(stored.get(index), transitions));
    }
    derived.forEach(region -> lines.add("    %% derived " + region.name()));
    return String.join("\n", lines) + "\n";
  }

  private static <E, F> List<String> block(Stored<F> region, List<Edge<E, F>> transitions) {
    List<String> lines = new ArrayList<>();
    lines.add("    state " + region.name() + " {");
    region
        .states()
        .forEach(
            state -> lines.add("        state \"" + state.name() + "\" as " + id(region, state)));
    lines.add("        [*] --> " + id(region, region.initial()));
    List<Edge<E, F>> own =
        transitions.stream().filter(t -> t.region().equals(region.name())).toList();
    Set<Integer> drawn = new HashSet<>();
    for (Edge<E, F> t : own) {
      List<Edge<E, F>> sharing =
          own.stream().filter(other -> t.group() > 0 && other.group() == t.group()).toList();
      if (sharing.size() < 2) {
        lines.add(
            "        "
                + id(region, t.from())
                + " --> "
                + id(region, t.to())
                + " : "
                + t.trigger().label());
      } else if (drawn.add(t.group())) {
        String source = region.name() + "_from_" + t.group();
        boolean loops = sharing.stream().allMatch(edge -> edge.from() == edge.to());
        lines.add("        state \"" + sources(region, sharing) + "\" as " + source);
        lines.add(
            "        "
                + source
                + " --> "
                + (loops ? source : id(region, t.to()))
                + " : "
                + t.trigger().label());
      }
    }
    region.states().stream()
        .filter(region.finals()::contains)
        .forEach(state -> lines.add("        " + id(region, state) + " --> [*]"));
    lines.add("    }");
    return lines;
  }

  private static <E, F> String sources(Stored<F> region, List<Edge<E, F>> sharing) {
    Set<Enum<?>> from = sharing.stream().map(Edge::from).collect(Collectors.toSet());
    if (from.size() * 2 <= region.states().size()) {
      return sharing.stream().map(edge -> edge.from().name()).collect(Collectors.joining(", "));
    }
    String left =
        region.states().stream()
            .filter(state -> !from.contains(state))
            .map(Enum::name)
            .collect(Collectors.joining(", "));
    return left.isEmpty() ? "all" : "all but " + left;
  }

  private static String id(Stored<?> region, Enum<?> state) {
    return region.name() + "_" + state.name();
  }
}

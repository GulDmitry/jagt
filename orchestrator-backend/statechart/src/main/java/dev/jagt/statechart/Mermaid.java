package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class Mermaid {

    private Mermaid() {
    }

    static <E, X, F> String render(List<Stored<F>> stored, List<Derived<X>> derived, List<Edge<E, F>> transitions) {
        List<String> lines = new ArrayList<>(List.of("stateDiagram-v2"));
        List<Stored<F>> top = stored.stream().filter(region -> region.scope().isEmpty()).toList();
        for (int index = 0; index < top.size(); index++) {
            if (index > 0) {
                lines.add("    --");
            }
            lines.addAll(block(top.get(index), "    state " + top.get(index).name() + " {", "    ", stored,
                    transitions));
        }
        derived.forEach(region -> lines.add("    %% derived " + region.name()));
        return String.join("\n", lines) + "\n";
    }

    private static <E, F> List<String> block(Stored<F> region, String header, String indent, List<Stored<F>> all,
                                             List<Edge<E, F>> transitions) {
        List<String> lines = new ArrayList<>();
        String in = indent + "    ";
        lines.add(header);
        region.states().forEach(state -> lines.add(in + "state \"" + state.name() + "\" as " + id(region, state)));
        lines.add(in + "[*] --> " + id(region, region.initial()));
        List<Edge<E, F>> own = transitions.stream().filter(t -> t.region().equals(region.name())).toList();
        Set<Integer> drawn = new HashSet<>();
        for (Edge<E, F> t : own) {
            List<Edge<E, F>> sharing = own.stream().filter(other -> t.group() > 0 && other.group() == t.group())
                    .toList();
            if (sharing.size() < 2) {
                lines.add(in + id(region, t.from()) + " --> " + id(region, t.to()) + " : " + t.label());
            } else if (drawn.add(t.group())) {
                lines.add(in + "state \"" + sources(region, sharing) + "\" as " + region.name() + "_from_" + t.group());
                lines.add(in + region.name() + "_from_" + t.group() + " --> " + id(region, t.to()) + " : "
                        + t.label());
            }
        }
        region.states().stream().filter(region.finals()::contains)
                .forEach(state -> lines.add(in + id(region, state) + " --> [*]"));
        for (Stored<F> child : all) {
            if (child.scope().filter(scope -> scope.parent().equals(region.name())).isPresent()) {
                lines.addAll(block(child, in + "state " + id(region, child.scope().orElseThrow().state()) + " {", in,
                        all, transitions));
            }
        }
        lines.add(indent + "}");
        return lines;
    }

    private static <E, F> String sources(Stored<F> region, List<Edge<E, F>> sharing) {
        Set<Enum<?>> from = sharing.stream().map(Edge::from).collect(Collectors.toSet());
        if (from.size() * 2 <= region.states().size()) {
            return sharing.stream().map(edge -> edge.from().name()).collect(Collectors.joining(", "));
        }
        String left = region.states().stream().filter(state -> !from.contains(state)).map(Enum::name)
                .collect(Collectors.joining(", "));
        return left.isEmpty() ? "all" : "all but " + left;
    }

    private static String id(Stored<?> region, Enum<?> state) {
        return region.name() + "_" + state.name();
    }
}

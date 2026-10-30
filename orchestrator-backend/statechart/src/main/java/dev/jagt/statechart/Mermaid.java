package dev.jagt.statechart;

import java.util.ArrayList;
import java.util.List;

final class Mermaid {

    private Mermaid() {
    }

    static <E, F> String render(List<Stored<F>> stored, List<Derived<E>> derived, List<Edge<E, F>> transitions) {
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
        region.states().forEach(state -> lines.add("        state \"" + state.name() + "\" as " + id(region, state)));
        lines.add("        [*] --> " + id(region, region.initial()));
        transitions.stream().filter(t -> t.region().equals(region.name())).forEach(t -> lines.add(
                "        " + id(region, t.from()) + " --> " + id(region, t.to()) + " : " + t.label()));
        region.states().stream().filter(region.finals()::contains)
                .forEach(state -> lines.add("        " + id(region, state) + " --> [*]"));
        lines.add("    }");
        return lines;
    }

    private static String id(Stored<?> region, Enum<?> state) {
        return region.name() + "_" + state.name();
    }
}

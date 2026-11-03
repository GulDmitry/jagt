package dev.jagt.orchestrator.port;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a text differs from an earlier one, in the line indexes of the later text: `changed` are the lines new or
 * rewritten, `removed` the lines that left, keyed by the index of the line they stood before.
 */
public record LineChange(Set<Integer> changed, Map<Integer, List<String>> removed) {

    public static LineChange none() {
        return new LineChange(Set.of(), Map.of());
    }
}

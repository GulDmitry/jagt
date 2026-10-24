package dev.jagt.orchestrator.stand;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum StandMode {

    JUDGE(Set.of("approve-plan", "ship", "deploy")),
    ACT(Set.of("approve-plan"));

    private final Set<String> humanVerbs;

    StandMode(Set<String> humanVerbs) {
        this.humanVerbs = humanVerbs;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean pressedByTheHuman(String verb) {
        return humanVerbs.contains(verb);
    }

    public static List<StandMode> selected(String ids) {
        if (ids.isBlank()) {
            return List.of(values());
        }
        return Arrays.stream(ids.split(",")).map(String::strip).map(id -> valueOf(id.toUpperCase(Locale.ROOT))).toList();
    }
}

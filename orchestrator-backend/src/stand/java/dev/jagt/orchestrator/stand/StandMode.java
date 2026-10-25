package dev.jagt.orchestrator.stand;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum StandMode {

    JUDGE(Set.of("approve-plan", "ship", "deploy"), true),
    ACT(Set.of("approve-plan"), false);

    private final Set<String> humanVerbs;
    private final boolean humanEndsTheRun;

    StandMode(Set<String> humanVerbs, boolean humanEndsTheRun) {
        this.humanVerbs = humanVerbs;
        this.humanEndsTheRun = humanEndsTheRun;
    }

    public boolean humanEndsTheRun() {
        return humanEndsTheRun;
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

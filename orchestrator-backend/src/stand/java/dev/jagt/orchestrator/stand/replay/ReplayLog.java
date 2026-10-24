package dev.jagt.orchestrator.stand.replay;

import java.nio.file.Path;
import java.util.List;

public record ReplayLog(List<String> divergences, boolean drained) {

    static final String DIVERGED = "replay diverged";
    static final String DRAINED = "replay drained";

    public static ReplayLog read(Path root) {
        List<BackendEvents.Event> events = BackendEvents.read(root);
        return new ReplayLog(events.stream().filter(event -> event.is(DIVERGED))
                .map(event -> event.field("step") + ": " + event.field("cause")).toList(),
                events.stream().anyMatch(event -> event.is(DRAINED)));
    }

    public boolean ended() {
        return drained || !divergences.isEmpty();
    }
}

package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record StandOutcome(String status, Duration took, List<String> tail, Path root) {

    public String describe() {
        return "last status " + status + " after " + took.toSeconds() + "s, root " + root + ", timeline tail:\n"
                + String.join("\n", tail);
    }
}

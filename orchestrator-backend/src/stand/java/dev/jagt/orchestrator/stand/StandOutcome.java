package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record StandOutcome(String status, String owner, Duration took, List<String> tail, Path root) {

    public boolean withTheHuman() {
        return owner.equals("YOU");
    }

    public boolean withTheCodeHost() {
        return owner.equals("CI");
    }

    public String describe() {
        return "last status " + status + " owned by " + owner + " after " + took.toSeconds() + "s, root " + root
                + ", timeline tail:\n" + String.join("\n", tail);
    }
}

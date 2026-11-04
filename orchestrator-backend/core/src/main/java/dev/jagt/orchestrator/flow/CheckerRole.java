package dev.jagt.orchestrator.flow;

import java.util.List;

/** A checker as its {@code role.yml} declares it: whether it runs jobs, and which brief steps open its read. */
public record CheckerRole(String id, boolean jobs, List<String> readSteps) {

    public CheckerRole {
        readSteps = List.copyOf(readSteps);
    }
}

package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.stand.StandShell;
import java.nio.file.Path;

public record GitAncestry(Path repository) implements Ancestry {

    @Override
    public boolean descends(String commit, String from) {
        return StandShell.gitSucceeds(repository, "merge-base", "--is-ancestor", from, commit);
    }
}

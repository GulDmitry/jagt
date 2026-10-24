package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.config.SessionCases;
import dev.jagt.orchestrator.stand.invariant.GitAncestry;
import dev.jagt.orchestrator.stand.invariant.InvariantChecker;
import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.stand.invariant.Violation;
import java.nio.file.Path;
import java.util.List;

public final class StandBooks {

    private final Path root;
    private final OrchestratorPaths paths;

    public StandBooks(Path root) {
        this.root = root;
        this.paths = new OrchestratorPaths(new OrchestratorProperties(root.toString(), null, null, null, null, null, null,
                false, null));
    }

    public SessionBrief brief() {
        return new SessionBrief(paths);
    }

    public CaseBook cases() {
        return new SessionCases(paths).book();
    }

    public List<Violation> violations() {
        return new InvariantChecker(cases(), new GitAncestry(root.resolve("origin.git"))).check(RunRecord.read(root));
    }
}

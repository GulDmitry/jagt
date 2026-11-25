package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.adapter.host.SystemHost;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.CaseBook;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.config.role.SessionCases;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.stand.invariant.GitAncestry;
import dev.jagt.orchestrator.stand.invariant.InvariantChecker;
import dev.jagt.orchestrator.stand.invariant.LegalMoves;
import dev.jagt.orchestrator.stand.invariant.RunRecord;
import dev.jagt.orchestrator.stand.invariant.Violation;
import java.nio.file.Path;
import java.util.List;

public final class StandBooks {

  private final Path root;
  private final OrchestratorPaths paths;

  private StandBooks(Path root, OrchestratorPaths paths) {
    this.root = root;
    this.paths = paths;
  }

  public static StandBooks at(Path root) {
    return new StandBooks(
        root,
        new OrchestratorPaths(
            OrchestratorProperties.defaults().withRoot(root.toString()), new SystemHost()));
  }

  public SessionBrief brief() {
    return new SessionBrief(paths);
  }

  public CaseBook cases() {
    return new SessionCases(paths).book();
  }

  public List<Violation> violations() {
    return new InvariantChecker(
            cases(),
            new GitAncestry(root.resolve("origin.git")),
            new LegalMoves(new FlowRules(new TaskChart(Composition.builtIn()))))
        .check(RunRecord.read(root));
  }
}

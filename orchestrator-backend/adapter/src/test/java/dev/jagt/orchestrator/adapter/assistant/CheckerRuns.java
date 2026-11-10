package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.config.RoleDefinitions;
import dev.jagt.orchestrator.port.CheckerReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class CheckerRuns {

  private CheckerRuns() {}

  static CheckerReader.Reach noReach() {
    return new CheckerReader.Reach(List.of(), List.of(), Optional.empty());
  }

  static CheckerReader.Reach workspaceReach(Path workspace) {
    return new CheckerReader.Reach(
        List.of(),
        List.of(workspace),
        Optional.of(new CheckerReader.Workspace(workspace, List.of())));
  }

  static String acceptanceAnswerSchema() {
    return RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
        .replies()
        .answer()
        .json();
  }

  static String acceptanceDeclineSchema() {
    return RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
        .replies()
        .decline()
        .json();
  }

  static CheckerReader.Run acceptanceRun(Path worktree) {
    return acceptanceRun(worktree, noReach());
  }

  static CheckerReader.Run acceptanceRun(Path worktree, CheckerReader.Reach reach) {
    return new CheckerReader.Run(
        "", "check ABC-42", worktree, "abc123", "", reach, acceptanceAnswerSchema(), "acceptance");
  }

  static CheckerReader.Run declineRun(Path worktree) {
    return new CheckerReader.Run(
        "", "decide", worktree, "abc123", "", noReach(), acceptanceDeclineSchema(), "acceptance");
  }

  static CheckerReader.Run run(Path worktree, String schema, String role) {
    return new CheckerReader.Run(
        "", "check ABC-42", worktree, "abc123", "", noReach(), schema, role);
  }
}

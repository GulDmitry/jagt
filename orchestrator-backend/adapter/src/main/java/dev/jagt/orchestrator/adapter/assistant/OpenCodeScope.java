package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What a read on OpenCode may touch: command globs it may and may not run, and path globs it may
 * not open.
 */
record OpenCodeScope(
    List<String> bashAllowed,
    List<String> bashRefused,
    List<String> readRefused,
    boolean fetch,
    boolean git,
    List<Path> roots,
    List<String> environments,
    Optional<CheckerReader.Workspace> workspace) {

  static OpenCodeScope of(
      List<String> shell,
      List<String> refuseShell,
      List<String> refuseRead,
      boolean fetch,
      boolean git,
      List<Path> roots,
      List<String> environments) {
    return new OpenCodeScope(
        shell, refuseShell, refuseRead, fetch, git, roots, environments, Optional.empty());
  }

  OpenCodeScope withWorkspace(Optional<CheckerReader.Workspace> held) {
    return new OpenCodeScope(
        bashAllowed, bashRefused, readRefused, fetch, git, roots, environments, held);
  }

  ReadScope fenced() {
    return new ReadScope(roots, List.of(), git, List.of(), fetch ? environments : List.of())
        .withWorkspace(workspace);
  }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.port.CheckerReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** What acceptance may touch beyond a worktree, read from its role options alone. */
public final class CheckerReach {

  private CheckerReach() {}

  public static CheckerReader.Reach of(RoleOptions acceptance) {
    String flowsPath = acceptance.flowsPathOrBlank();
    Optional<CheckerReader.Workspace> workspace = workspace(acceptance);
    return new CheckerReader.Reach(
        urls(acceptance),
        Stream.concat(
                flowsPath.isEmpty() ? Stream.<Path>empty() : Stream.of(Path.of(flowsPath)),
                workspace.stream().map(CheckerReader.Workspace::root))
            .toList(),
        workspace);
  }

  public static Optional<CheckerReader.Workspace> workspace(RoleOptions acceptance) {
    return acceptance.workspacePath().map(root -> opened(root, acceptance));
  }

  private static CheckerReader.Workspace opened(Path root, RoleOptions acceptance) {
    try {
      Files.createDirectories(root);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot create the workspace " + root, e);
    }
    String flows = String.join(" ", acceptance.flows().command());
    return new CheckerReader.Workspace(
        root,
        flows.isEmpty()
            ? List.of()
            : acceptance.environments().stream()
                .map(environment -> "BASE_URL=" + environment.url() + " " + flows)
                .toList());
  }

  private static List<String> urls(RoleOptions acceptance) {
    return acceptance.environments().stream()
        .flatMap(environment -> Stream.of(environment.url(), environment.landed()))
        .filter(url -> !url.isBlank())
        .distinct()
        .toList();
  }
}

package dev.jagt.orchestrator.adapter.worktree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * Commands named like the container engine's, which run a stack as the name file of the worktree
 * they run in: a session's one environment cannot, since it may walk into another worktree.
 */
final class ComposeShims {

  private static final Path DIRECTORY = Path.of(".jagt", "bin");
  private static final List<String> COMMANDS = List.of("docker", "docker-compose");

  private static final Path NAME_FILE = Path.of(".jagt", "compose-project");

  private static final String SCRIPT =
      """
      #!/bin/sh
      dir=$PWD
      while [ -n "$dir" ]; do
        if [ -f "$dir/.jagt/compose-project" ]; then
          COMPOSE_PROJECT_NAME=$(cat "$dir/.jagt/compose-project")
          export COMPOSE_PROJECT_NAME
          break
        fi
        dir=${dir%/*}
      done
      self=$(cd "$(dirname "$0")" && pwd)
      name=${0##*/}
      IFS=:
      for entry in $PATH; do
        [ "$entry" = "$self" ] && continue
        [ -x "$entry/$name" ] && exec "$entry/$name" "$@"
      done
      echo "$name: not found on PATH" >&2
      exit 127
      """;

  private ComposeShims() {}

  static void writeStackName(Path worktree, String project) throws IOException {
    Path file = worktree.resolve(NAME_FILE);
    Files.createDirectories(file.getParent());
    Files.writeString(file, project);
  }

  static Path install(Path worktree) throws IOException {
    Path directory = worktree.resolve(DIRECTORY);
    Files.createDirectories(directory);
    for (String command : COMMANDS) {
      Path script = directory.resolve(command);
      Files.writeString(script, SCRIPT);
      Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
    return directory;
  }
}

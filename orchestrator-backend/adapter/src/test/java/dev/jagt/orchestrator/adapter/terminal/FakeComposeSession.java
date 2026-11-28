package dev.jagt.orchestrator.adapter.terminal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

final class FakeComposeSession {

  private final Path fakeBin;

  FakeComposeSession(Path root) throws IOException {
    fakeBin = Files.createDirectories(root.resolve("fake-bin"));
    Path docker = fakeBin.resolve("docker");
    Files.writeString(docker, "#!/bin/sh\necho \"$COMPOSE_PROJECT_NAME\"\n");
    Files.setPosixFilePermissions(docker, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  String composeProject(String sessionPrefix, Path directory)
      throws IOException, InterruptedException {
    ProcessBuilder shell =
        new ProcessBuilder(
            "sh",
            "-c",
            sessionPrefix + "sh -c 'cd \"$1\" && docker compose ps' sh '" + directory + "'");
    shell.environment().put("PATH", fakeBin + ":" + System.getenv("PATH"));
    Process run = shell.start();
    String project = new String(run.getInputStream().readAllBytes(), UTF_8).strip();
    run.waitFor();
    return project;
  }
}

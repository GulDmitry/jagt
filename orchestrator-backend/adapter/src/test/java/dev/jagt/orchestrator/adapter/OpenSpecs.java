package dev.jagt.orchestrator.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Host;
import dev.jagt.orchestrator.port.Processes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

final class OpenSpecs {

  static final List<String> VALIDATE =
      List.of(
          "/bin/openspec",
          "validate",
          "abc-42",
          "--type",
          "change",
          "--strict",
          "--no-interactive",
          "--json");

  static final List<String> ARCHIVE = List.of("/bin/openspec", "archive", "abc-42", "-y");

  static final Processes.Result VALID =
      new Processes.Result(0, "{\"items\":[{\"valid\":true,\"issues\":[]}]}", "");

  private OpenSpecs() {}

  static Processes openChangeValidatedAs(Host host, Path worktree, Processes.Result result)
      throws IOException {
    when(host.resolve("openspec")).thenReturn("/bin/openspec");
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), eq(VALIDATE)))
        .thenReturn(result);
    return processes;
  }

  static Processes openChangeAnsweringEveryCommand(
      Host host, Path worktree, String cli, Processes.Result result) throws IOException {
    when(host.resolve("openspec")).thenReturn(cli);
    Files.createDirectories(worktree.resolve("openspec/changes/abc-42"));
    Processes processes = mock(Processes.class);
    when(processes.run(eq(worktree), any(Duration.class), anyMap(), any())).thenReturn(result);
    return processes;
  }
}

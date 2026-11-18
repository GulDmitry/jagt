package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.With;

@With
public record TestTmuxServerBuilder(Path directory, Path root) {

  public record Server(Path directory, Path socket) {

    public boolean running() {
      return StandShell.run(directory, List.of("tmux", "-S", socket.toString(), "has-session"))
              .exit()
          == 0;
    }

    public void stop() {
      StandShell.run(directory, List.of("tmux", "-S", socket.toString(), "kill-server"));
    }
  }

  public static TestTmuxServerBuilder tmuxServer(Path directory, Path root) {
    return new TestTmuxServerBuilder(directory, root);
  }

  public Server build() throws IOException {
    Path socket = Files.createTempDirectory(directory, "s").resolve("d");
    String lifeline =
        "while kill -0 " + ProcessHandle.current().pid() + " 2>/dev/null; do sleep 1; done";
    StandShell.run(
        directory,
        List.of(
            "tmux",
            "-S",
            socket.toString(),
            "new-session",
            "-d",
            "-c",
            root.toString(),
            "sh",
            "-c",
            lifeline));
    return new Server(directory, socket);
  }
}

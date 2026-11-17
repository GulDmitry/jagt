package dev.jagt.orchestrator;

import java.nio.file.Path;
import lombok.With;

@With
public record TestLaunchArgsBuilder(Path root, int port) {

  public static TestLaunchArgsBuilder launchArgs(Path root, int port) {
    return new TestLaunchArgsBuilder(root, port);
  }

  public String[] build() {
    return new String[] {
      "--server.port=" + port,
      "--orchestrator.terminal=none",
      "--orchestrator.tmux-command=" + root.resolve("tmux"),
      "--orchestrator.root=" + root,
      "--orchestrator.data=" + root,
      "--spring.config.import=",
      "--orchestrator.config-file=" + root.resolve("jagt.yml"),
      "--orchestrator.state-file=" + root.resolve("state.json"),
      "--logging.file.name=" + root.resolve("jagt.log")
    };
  }
}

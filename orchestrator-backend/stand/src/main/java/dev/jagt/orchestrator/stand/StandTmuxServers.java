package dev.jagt.orchestrator.stand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class StandTmuxServers {

  private StandTmuxServers() {}

  public static void stopOrphaned(Path base) {
    ProcessHandle.allProcesses()
        .filter(process -> orphaned(process.info(), base))
        .forEach(StandShell::stopTree);
  }

  private static boolean orphaned(ProcessHandle.Info info, Path base) {
    boolean tmux =
        info.command()
            .map(command -> Path.of(command).getFileName().toString().equals("tmux"))
            .orElse(false);
    return tmux && info.arguments().map(arguments -> goneRootIn(arguments, base)).orElse(false);
  }

  private static boolean goneRootIn(String[] arguments, Path base) {
    List<String> list = List.of(arguments);
    int flag = list.indexOf("-c");
    if (flag < 0 || flag + 1 >= list.size()) {
      return false;
    }
    Path root = Path.of(list.get(flag + 1));
    return base.equals(root.getParent())
        && StandRoots.named(Objects.requireNonNull(root.getFileName()))
        && Files.notExists(root);
  }
}

package dev.jagt.orchestrator.stand;

/** An engine whose jagt runs in a container or pod, seen only through what runs beside it. */
public interface UnitEngine extends JagtEngine {

  String JAR = "jagt.jar";

  @Override
  default long pid() {
    return exec("pgrep", "-f", JAR)
        .output()
        .lines()
        .map(String::strip)
        .filter(line -> line.matches("\\d+"))
        .findFirst()
        .map(Long::parseLong)
        .orElse(0L);
  }

  @Override
  default String windows() {
    StandShell.Result windows = exec("tmux", "list-windows", "-a", "-F", "#{window_name}");
    return windows.exit() == 0 ? windows.output().strip().replace('\n', ' ') : "";
  }

  @Override
  default String screens() {
    StandShell.Result screens =
        exec(
            "sh",
            "-c",
            "tmux list-windows -a -F '#{session_name}:#{window_index} #{window_name}'"
                + " | while read -r target name; do echo \"=== $target $name\";"
                + " tmux capture-pane -p -S -300 -t \"$target\"; done");
    return screens.exit() == 0 ? screens.output() : "";
  }
}

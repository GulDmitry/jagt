package technical.maintainability.modularity;

import dev.jagt.orchestrator.RingFiles;
import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class EdgeNamesFitness implements Fitness {

  private static final List<String> OS_ONLY =
      List.of(
          "osascript",
          "notify-send",
          "setsid",
          "/opt/homebrew",
          "/usr/local/bin",
          "wt.exe",
          "PATHEXT",
          "lsof",
          "kill -9",
          "/Applications");

  static final List<String> VENDORS =
      List.of(
          "claude",
          "codex",
          "kitty",
          "tmux",
          "herdr",
          "intellij",
          "jetbrains",
          "jira",
          "gitlab",
          "github",
          "anthropic",
          "openai",
          "mcp__");

  private static final List<String> IDE_FOLDERS = List.of(".idea", ".vscode", ".fleet");

  private static final String RETIRED_KEY_TABLE = "RenamedKeys.java";

  private static final List<String> WORKTREE_FILLING =
      List.of("WorktreeNoise.java", "WorktreeFiles.java");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        Stream.of(
                osNamed().map(name -> "os named outside the edge: " + name),
                vendorNamed().map(name -> "vendor named outside the edge: " + name),
                ideFolderNamed().map(name -> "ide folder named outside the edge: " + name))
            .flatMap(items -> items)
            .toList());
  }

  private static Stream<Path> osNamed() {
    return RingFiles.outsideTheEdge(true)
        .filter(path -> OS_ONLY.stream().anyMatch(name -> SourceRoots.read(path).contains(name)))
        .map(Path::getFileName);
  }

  private static Stream<Path> vendorNamed() {
    return RingFiles.outsideTheEdge(false)
        .filter(path -> !path.getFileName().toString().equals(RETIRED_KEY_TABLE))
        .filter(path -> {
          String text = SourceRoots.read(path).toLowerCase(Locale.ROOT);
          return VENDORS.stream().anyMatch(text::contains);
        })
        .map(Path::getFileName);
  }

  private static Stream<Path> ideFolderNamed() {
    return RingFiles.outsideTheEdge(false)
        .filter(path -> !WORKTREE_FILLING.contains(path.getFileName().toString()))
        .filter(path -> IDE_FOLDERS.stream().anyMatch(SourceRoots.read(path)::contains))
        .map(Path::getFileName);
  }
}

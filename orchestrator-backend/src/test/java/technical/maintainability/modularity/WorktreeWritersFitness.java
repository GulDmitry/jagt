package technical.maintainability.modularity;

import dev.jagt.orchestrator.SourceRoots;
import dev.jagt.orchestrator.task.plan.Artifact;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorktreeWritersFitness implements Fitness {

  private static final Pattern NAMED =
      Pattern.compile("\\bworktree(?:\\(\\))?\\.resolve\\((?:[^\"()]|\\(\\))*?\"([^\"]+)\"\\)");

  private static final Set<String> RUNTIME_CONFIG = Set.of("mcp_client.js");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    List<String> unlisted = new ArrayList<>();
    SourceRoots.mainJavaFiles().forEach(file -> unlisted.addAll(unlistedIn(file)));
    return Measurement.of(unlisted);
  }

  private static List<String> unlistedIn(Path file) {
    List<String> unlisted = new ArrayList<>();
    Matcher named = NAMED.matcher(SourceRoots.read(file));
    while (named.find()) {
      String name = named.group(1);
      if (!listed(name)) {
        unlisted.add(file + ": worktree.resolve(\"" + name + "\") is no Artifact");
      }
    }
    return unlisted;
  }

  private static boolean listed(String name) {
    return name.startsWith(".")
        || RUNTIME_CONFIG.contains(name)
        || Artifact.fileNames().contains(name)
        || Artifact.expandable().stream().anyMatch(pattern -> covers(pattern, name));
  }

  private static boolean covers(String pattern, String name) {
    if (pattern.endsWith("/")) {
      return (name + "/").equals(pattern);
    }
    return name.endsWith(pattern.substring(pattern.indexOf('*') + 1));
  }
}

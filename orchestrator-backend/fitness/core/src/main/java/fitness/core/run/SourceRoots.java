package fitness.core.run;

import fitness.core.FitnessConfigException;
import fitness.core.FitnessFunction;
import fitness.core.Project;
import fitness.core.Reason;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** The Java source roots of a project, found at module depth, and the class directory each compiles to. */
public final class SourceRoots {

  private static final int DEPTH = 4;
  private static final String MAIN = "main";

  private SourceRoots() {}

  /**
   * A set is a comma-separated list of {@code main}, {@code test} (every set but main), {@code all}
   * or a source set's own name; only the project's {@code sourceSets} are candidates, and a name
   * matching no candidate root is an error.
   */
  public static List<Path> of(Project project, String set) {
    List<String> names = Stream.of(set.split(",")).map(String::trim).toList();
    List<Path> roots =
        roots(project.root()).stream()
            .filter(root -> project.sourceSets().stream().anyMatch(each -> selects(each, setOf(root))))
            .toList();
    for (String name : names) {
      if (roots.stream().noneMatch(root -> selects(name, setOf(root)))) {
        throw new FitnessConfigException(Reason.UNKNOWN_SELECTOR, "set: " + name);
      }
    }
    return roots.stream()
        .filter(root -> names.stream().anyMatch(name -> selects(name, setOf(root))))
        .toList();
  }

  /** Fails the load when an explicit {@code set} of the function names no source root of the project. */
  public static void validate(FitnessFunction function, Project project) {
    if (!(function.selectors().get("set") instanceof String set)) {
      return;
    }
    try {
      of(project, set);
    } catch (FitnessConfigException e) {
      throw new FitnessConfigException(e.reason(), function.section() + "." + function.id() + ".set", e);
    }
  }

  public static Path classes(Path projectRoot, Path sourceRoot) {
    Path inside = projectRoot.relativize(sourceRoot);
    Path module =
        inside.getName(0).toString().equals("src")
            ? projectRoot
            : projectRoot.resolve(inside.getName(0));
    return module.resolve("build/classes/java").resolve(sourceRoot.getParent().getFileName());
  }

  private static List<Path> roots(Path projectRoot) {
    SourceRootWalk walk = new SourceRootWalk(SourceRoots::isRoot);
    try {
      Files.walkFileTree(projectRoot, Set.of(), DEPTH, walk);
      return walk.roots().stream().sorted(Comparator.naturalOrder()).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean isRoot(Path path) {
    return path.getNameCount() >= 3
        && path.getFileName().toString().equals("java")
        && path.getParent().getParent().getFileName().toString().equals("src");
  }

  private static String setOf(Path root) {
    return root.getParent().getFileName().toString();
  }

  private static boolean selects(String name, String setOfRoot) {
    return switch (name) {
      case "all" -> true;
      case "test" -> !setOfRoot.equals(MAIN);
      default -> name.equals(setOfRoot);
    };
  }
}

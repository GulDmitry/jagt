package fitness.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** The Java source roots of a project, found at module depth, and the class directory each compiles to. */
public final class SourceRoots {

  private static final int DEPTH = 4;
  private static final String MAIN = "main";

  private SourceRoots() {}

  /**
   * A set is a comma-separated list of {@code main}, {@code test} (every set but main), {@code all}
   * or a source set's own name; a name matching no root is an error.
   */
  public static List<Path> of(Path projectRoot, String set) {
    List<String> names = Stream.of(set.split(",")).map(String::trim).toList();
    List<Path> roots = roots(projectRoot);
    for (String name : names) {
      if (roots.stream().noneMatch(root -> selects(name, setOf(root)))) {
        throw new IllegalArgumentException("no source root in set: " + name);
      }
    }
    return roots.stream()
        .filter(root -> names.stream().anyMatch(name -> selects(name, setOf(root))))
        .toList();
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
    try (Stream<Path> tree = Files.walk(projectRoot, DEPTH)) {
      return tree.filter(Files::isDirectory)
          .filter(SourceRoots::isRoot)
          .sorted(Comparator.naturalOrder())
          .toList();
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

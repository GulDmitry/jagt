package fitness.gradle;

import fitness.core.Presence;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;

/** Which tools a build has: the Gradle plugins it applies, and the executables on its PATH. */
final class Installed implements Presence {

  private static final Map<String, String> PLUGINS =
      Map.of("checkstyle", "checkstyle", "jacoco", "jacoco", "pit", "info.solidsoft.pitest");
  private static final Set<String> EXECUTABLES = Set.of("osv-scanner");

  private final Set<String> plugins;
  private final String path;

  Installed(Set<String> plugins, String path) {
    this.plugins = plugins;
    this.path = path;
  }

  static Installed of(Project root) {
    Set<String> applied = new HashSet<>();
    for (Project project : root.getAllprojects()) {
      PLUGINS.values().stream()
          .filter(id -> project.getPluginManager().hasPlugin(id))
          .forEach(applied::add);
    }
    String path = System.getenv("PATH");
    return new Installed(applied, path == null ? "" : path);
  }

  @Override
  public boolean has(String need) {
    if (PLUGINS.containsKey(need)) {
      return plugins.contains(PLUGINS.get(need));
    }
    if (EXECUTABLES.contains(need)) {
      return Arrays.stream(path.split(File.pathSeparator))
          .map(dir -> Path.of(dir).resolve(need))
          .anyMatch(Files::isExecutable);
    }
    return true;
  }
}

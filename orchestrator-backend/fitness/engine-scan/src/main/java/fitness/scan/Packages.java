package fitness.scan;

import fitness.core.FitnessFunction;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Packages {

  private static final String ROOT = "(root)";
  private static final Pattern SOURCE_ROOT = Pattern.compile("(?:^|/)src/[^/]+/java/?(.*)$");

  private Packages() {}

  public static List<Sized> classes(FitnessFunction function, Project project, List<Path> files) {
    String base = project.basePackage().replace('.', '/');
    Map<String, Long> counts = new TreeMap<>();
    for (Path file : files) {
      Matcher inside = SOURCE_ROOT.matcher(Sources.relative(project, file.getParent()));
      if (inside.find() && !file.getFileName().toString().equals("package-info.java")) {
        counts.merge(withinBase(inside.group(1), base), 1L, Long::sum);
      }
    }
    return counts.entrySet().stream()
        .filter(entry -> !entry.getKey().isEmpty())
        .map(entry -> new Sized(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static String withinBase(String path, String base) {
    if (base.isEmpty()) {
      return path;
    }
    if (path.equals(base)) {
      return ROOT;
    }
    return path.startsWith(base + "/") ? path.substring(base.length() + 1) : "";
  }
}

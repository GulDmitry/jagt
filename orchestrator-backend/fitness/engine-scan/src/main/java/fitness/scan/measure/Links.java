package fitness.scan.measure;

import fitness.core.FitnessFunction;
import fitness.core.Project;
import fitness.scan.Sources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Links {

  private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s#]+)(?:#[^)]*)?\\)");
  private static final Pattern EXTERNAL = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");

  private Links() {}

  public static List<String> unresolved(FitnessFunction function, Project project, List<Path> files) {
    List<String> found = new ArrayList<>();
    for (Path file : files) {
      for (Path target : targets(file)) {
        if (!Files.exists(target)) {
          found.add(Sources.relative(project, file) + " -> " + file.getParent().relativize(target));
        }
      }
    }
    return found;
  }

  public static List<String> nested(FitnessFunction function, Project project, List<Path> files) {
    List<String> found = new ArrayList<>();
    for (Path file : files) {
      Path home = file.getParent();
      for (Path target : targets(file)) {
        if (Files.isRegularFile(target)
            && target.startsWith(home)
            && !target.equals(file)
            && targets(target).stream().anyMatch(next -> next.startsWith(home))) {
          found.add(Sources.relative(project, file) + " -> " + home.relativize(target));
        }
      }
    }
    return found;
  }

  private static List<Path> targets(Path file) {
    List<Path> targets = new ArrayList<>();
    Matcher matcher = LINK.matcher(Sources.read(file));
    while (matcher.find()) {
      String target = matcher.group(1);
      if (!EXTERNAL.matcher(target).matches()) {
        targets.add(file.getParent().resolve(target).normalize());
      }
    }
    return targets;
  }
}

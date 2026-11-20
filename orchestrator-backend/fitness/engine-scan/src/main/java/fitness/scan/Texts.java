package fitness.scan;

import fitness.core.Project;
import fitness.core.FitnessFunction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Texts {

  private static final List<String> COMMENT_STARTS = List.of("//", "/*", "*");

  private Texts() {}

  static List<Sized> fileLines(FitnessFunction function, Project project, List<Path> files) {
    return files.stream()
        .map(file -> new Sized(Sources.relative(project, file), Sources.read(file).lines().count()))
        .toList();
  }

  static List<Sized> sectionLines(FitnessFunction function, Project project, List<Path> files) {
    Pattern marker = Sources.requiredRegex(function);
    List<Sized> sections = new ArrayList<>();
    for (Path file : files) {
      String text = Sources.read(file);
      Matcher heading = marker.matcher(text);
      String label = Sources.relative(project, file);
      int from = 0;
      while (heading.find()) {
        sections.add(new Sized(label, text.substring(from, heading.start()).lines().count()));
        label = Sources.relative(project, file) + " " + heading.group();
        from = heading.end();
      }
      sections.add(new Sized(label, text.substring(from).lines().count()));
    }
    return sections;
  }

  static List<Sized> lineCharacters(FitnessFunction function, Project project, List<Path> files) {
    Pattern filter = Sources.regex(function);
    List<Sized> lines = new ArrayList<>();
    for (Path file : files) {
      for (String line : Sources.read(file).lines().toList()) {
        if (filter == null || filter.matcher(line).find()) {
          lines.add(new Sized(Sources.relative(project, file) + ": " + line.strip(), line.length()));
        }
      }
    }
    return lines;
  }

  static List<Sized> commentPercent(FitnessFunction function, Project project, List<Path> files) {
    long code = 0;
    long comments = 0;
    for (Path file : files) {
      for (String line : Sources.read(file).lines().map(String::strip).toList()) {
        code += line.isEmpty() ? 0 : 1;
        comments += COMMENT_STARTS.stream().anyMatch(line::startsWith) ? 1 : 0;
      }
    }
    if (code == 0) {
      return List.of();
    }
    return List.of(new Sized(comments + " comment lines in " + code, 100 * comments / code));
  }
}

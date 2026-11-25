package technical.maintainability.analysability;

import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SilentCatchFitness implements Fitness {

  private static final Pattern CATCH = Pattern.compile("\\bcatch \\(");
  private static final Pattern REPORTS = Pattern.compile("\\blog\\.|\\bthrow\\b");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    List<String> silent = new ArrayList<>();
    SourceRoots.mainJavaFiles().forEach(file -> silent.addAll(silentCatchesIn(file)));
    return Measurement.of(silent);
  }

  private static List<String> silentCatchesIn(Path file) {
    String source = SourceRoots.read(file);
    List<String> found = new ArrayList<>();
    Matcher catches = CATCH.matcher(source);
    while (catches.find()) {
      int open = source.indexOf('{', catches.end());
      if (open >= 0 && !REPORTS.matcher(bodyFrom(source, open)).find()) {
        found.add(file + ":" + lineOf(source, catches.start()));
      }
    }
    return found;
  }

  private static String bodyFrom(String source, int open) {
    int depth = 0;
    for (int at = open; at < source.length(); at++) {
      char c = source.charAt(at);
      depth += c == '{' ? 1 : c == '}' ? -1 : 0;
      if (depth == 0) {
        return source.substring(open, at);
      }
    }
    return source.substring(open);
  }

  private static long lineOf(String source, int offset) {
    return source.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
  }
}

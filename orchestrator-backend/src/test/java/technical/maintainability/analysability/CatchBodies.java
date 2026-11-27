package technical.maintainability.analysability;

import dev.jagt.orchestrator.SourceRoots;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CatchBodies {

  private CatchBodies() {}

  /**
   * The {@code file:line} of each catch matching {@code catches} whose body never matches {@code
   * reports}.
   */
  static List<String> without(Path file, Pattern catches, Pattern reports) {
    String source = SourceRoots.read(file);
    List<String> found = new ArrayList<>();
    Matcher matcher = catches.matcher(source);
    while (matcher.find()) {
      int open = source.indexOf('{', matcher.end());
      if (open >= 0 && !reports.matcher(bodyFrom(source, open)).find()) {
        found.add(file + ":" + lineOf(source, matcher.start()));
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

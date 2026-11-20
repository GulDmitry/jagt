package fitness.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A glob over `/`-separated paths: `**` crosses directories, `**` + `/` may match nothing, `{a,b}` alternates; a comma
 * outside braces is a letter.
 */
record Glob(Path base, Pattern pattern) {

  private static final String META = "*?{";

  static Glob of(Path root, String glob) {
    List<String> segments = List.of(glob.split("/"));
    int literal = 0;
    while (literal < segments.size() - 1 && !hasMeta(segments.get(literal))) {
      literal++;
    }
    Path base = root;
    for (String segment : segments.subList(0, literal)) {
      base = base.resolve(segment);
    }
    String rest = String.join("/", segments.subList(literal, segments.size()));
    return new Glob(base.normalize(), Pattern.compile(translate(rest)));
  }

  static List<String> split(String globs) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < globs.length(); i++) {
      char c = globs.charAt(i);
      depth += depthChange(c);
      if (c == ',' && depth == 0) {
        parts.add(globs.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(globs.substring(start));
    return parts;
  }

  static boolean isLiteral(String glob) {
    return !hasMeta(glob);
  }

  boolean matches(String relative) {
    return pattern.matcher(relative).matches();
  }

  private static boolean hasMeta(String segment) {
    return segment.chars().anyMatch(c -> META.indexOf(c) >= 0);
  }

  private static int depthChange(char c) {
    if (c == '{') {
      return 1;
    }
    return c == '}' ? -1 : 0;
  }

  private static String translate(String glob) {
    StringBuilder regex = new StringBuilder();
    int depth = 0;
    int i = 0;
    while (i < glob.length()) {
      if (glob.startsWith("**/", i)) {
        regex.append("(?:.*/)?");
        i += 3;
      } else if (glob.startsWith("**", i)) {
        regex.append(".*");
        i += 2;
      } else {
        depth += depthChange(glob.charAt(i));
        regex.append(single(glob.charAt(i), depth));
        i++;
      }
    }
    return regex.toString();
  }

  private static String single(char c, int depth) {
    return switch (c) {
      case '*' -> "[^/]*";
      case '?' -> "[^/]";
      case '{' -> "(?:";
      case '}' -> ")";
      case ',' -> depth > 0 ? "|" : ",";
      default -> Pattern.quote(String.valueOf(c));
    };
  }
}

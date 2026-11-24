package fitness.scan;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The paths of the machine running the check, as one pattern: each in its own, the other separator's and the '-'-encoded spelling. */
final class MachinePaths {

  private MachinePaths() {}

  static Pattern running() {
    String tmp = System.getProperty("java.io.tmpdir");
    return of(System.getProperty("user.home"), tmp, real(tmp));
  }

  static Pattern of(String home, String... temporaries) {
    List<String> paths =
        Stream.concat(Stream.of(home), Stream.of(temporaries))
            .filter(MachinePaths::deep)
            .map(path -> path.replaceAll("[/\\\\]+$", ""))
            .filter(path -> !path.isEmpty())
            .distinct()
            .toList();
    return Pattern.compile(
        paths.stream()
            .flatMap(
                path ->
                    Stream.of(
                        path,
                        path.replace('/', '\\'),
                        path.replace('\\', '/'),
                        path.replaceAll("[/\\\\]", "-")))
            .distinct()
            .map(Pattern::quote)
            .reduce((left, right) -> left + "|" + right)
            .orElse("(?!)"));
  }

  private static boolean deep(String path) {
    return Stream.of(path.split("[/\\\\]+")).filter(name -> !name.isEmpty()).count() > 1;
  }

  private static String real(String path) {
    try {
      return Path.of(path).toRealPath().toString();
    } catch (IOException e) {
      return path;
    }
  }
}

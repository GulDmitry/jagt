package fitness.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The Spring contexts a suite booted: the sum, over its caches, of the cache's own miss count logged into the suite's reports. */
final class Contexts {

  private static final Pattern MISSES =
      Pattern.compile("DefaultContextCache@(\\w+) .*?missCount = (\\d+)");

  private Contexts() {}

  static long created(Path reports) {
    if (!Files.isDirectory(reports)) {
      return 0;
    }
    Map<String, Long> caches = new HashMap<>();
    try (Stream<Path> files = Files.list(reports)) {
      for (Path xml : files.filter(path -> path.toString().endsWith(".xml")).toList()) {
        Matcher matcher = MISSES.matcher(Files.readString(xml));
        while (matcher.find()) {
          caches.merge(matcher.group(1), Long.parseLong(matcher.group(2)), Math::max);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return caches.values().stream().mapToLong(Long::longValue).sum();
  }
}

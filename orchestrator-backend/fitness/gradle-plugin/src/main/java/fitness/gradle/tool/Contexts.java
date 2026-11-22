package fitness.gradle.tool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The contexts a suite booted: the sum, over its caches, of the cache's own miss count logged into the suite's reports. */
public final class Contexts {

  private Contexts() {}

  public static long created(Path reports, Pattern misses) {
    if (!Files.isDirectory(reports)) {
      return 0;
    }
    Map<String, Long> caches = new HashMap<>();
    try (Stream<Path> files = Files.list(reports)) {
      for (Path xml : files.filter(path -> path.toString().endsWith(".xml")).toList()) {
        Matcher matcher = misses.matcher(Files.readString(xml));
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

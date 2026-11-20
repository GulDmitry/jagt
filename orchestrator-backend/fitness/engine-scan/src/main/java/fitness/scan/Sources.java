package fitness.scan;

import fitness.core.FitnessConfigException;
import fitness.core.Project;
import fitness.core.Reason;
import fitness.core.FitnessFunction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class Sources {

  private Sources() {}

  static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String relative(Project project, Path path) {
    return project.root().relativize(path).toString().replace('\\', '/');
  }

  @Nullable
  static Pattern regex(FitnessFunction function) {
    return function.selectors().get("regex") instanceof String text ? Pattern.compile(text) : null;
  }

  static Pattern requiredRegex(FitnessFunction function) {
    Pattern regex = regex(function);
    if (regex == null) {
      throw new FitnessConfigException(Reason.MISSING_KEY, function.id() + ".regex");
    }
    return regex;
  }
}

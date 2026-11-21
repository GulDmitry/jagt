package fitness.scan;

import fitness.core.FitnessConfigException;
import fitness.core.FitnessFunction;
import fitness.core.Project;
import fitness.core.Reason;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class Sources {

  private Sources() {}

  public static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String relative(Project project, Path path) {
    return project.root().relativize(path).toString().replace('\\', '/');
  }

  @Nullable
  public static Pattern regex(FitnessFunction function) {
    return function.selectors().get("regex") instanceof String text ? Pattern.compile(text) : null;
  }

  public static Pattern requiredRegex(FitnessFunction function) {
    Pattern regex = regex(function);
    if (regex == null) {
      throw new FitnessConfigException(Reason.MISSING_KEY, function.id() + ".regex");
    }
    return regex;
  }
}

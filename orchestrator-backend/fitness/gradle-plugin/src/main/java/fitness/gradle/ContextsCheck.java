package fitness.gradle;

import fitness.core.Measurement;
import java.util.List;
import java.util.regex.Pattern;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public abstract class ContextsCheck extends FitnessFunctionCheck {

  @InputDirectory
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract DirectoryProperty getReports();

  @Input
  public abstract Property<String> getPattern();

  @Override
  protected Measurement measure() {
    long created = Contexts.created(getReports().get().getAsFile().toPath(), Pattern.compile(getPattern().get()));
    return new Measurement(created, List.of(created + " contexts"));
  }
}

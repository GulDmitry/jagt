package fitness.gradle;

import fitness.core.Measurement;
import java.util.List;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public abstract class CoverageCheck extends FitnessFunctionCheck {

  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getReport();

  @Override
  protected Measurement measure() {
    long percent = Reach.linePercent(getReport().get().getAsFile().toPath());
    return new Measurement(percent, List.of(percent + " percent of lines covered"));
  }
}

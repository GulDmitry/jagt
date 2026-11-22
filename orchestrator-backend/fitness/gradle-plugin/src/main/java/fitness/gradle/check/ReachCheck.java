package fitness.gradle.check;

import fitness.core.Measurement;
import fitness.gradle.tool.Reach;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public abstract class ReachCheck extends FitnessFunctionCheck {

  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getReport();

  @Input
  public abstract MapProperty<String, String> getClassDirectories();

  @Override
  protected Measurement measure() {
    Map<String, Path> directories = new LinkedHashMap<>();
    getClassDirectories()
        .get()
        .forEach((module, dir) -> directories.put(module, new File(dir).toPath()));
    return Measurement.of(Reach.unreached(getReport().get().getAsFile().toPath(), directories));
  }
}

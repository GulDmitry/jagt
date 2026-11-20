package fitness.gradle;

import fitness.core.Direction;
import fitness.core.Measurement;
import fitness.core.Report;
import fitness.core.FitnessFunction;
import fitness.core.Threshold;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Measures one tool function and fails the build when the value breaks its threshold; one measured elsewhere or skipped as optional is named. */
@DisableCachingByDefault(because = "it measures the machine as it is now")
public abstract class FitnessFunctionCheck extends DefaultTask {

  @Input
  public abstract Property<String> getFitnessFunctionId();

  @Input
  public abstract Property<String> getDirection();

  @Input
  public abstract Property<Long> getLimit();

  @Input
  @Optional
  public abstract Property<String> getHint();

  @Input
  public abstract Property<Boolean> getOptional();

  @Internal
  public abstract DirectoryProperty getLastValues();

  protected abstract Measurement measure();

  @TaskAction
  public void check() {
    Measurement measurement;
    try {
      measurement = measure();
    } catch (Noted noted) {
      getLogger().lifecycle("fitness: {}: {}", getFitnessFunctionId().get(), noted.getMessage());
      return;
    }
    lastValues().record(getFitnessFunctionId().get(), measurement.value());
    Map<String, Object> selectors =
        getHint().isPresent() ? Map.of("hint", getHint().get()) : Map.of();
    FitnessFunction function =
        new FitnessFunction(
            getFitnessFunctionId().get(),
            "",
            FitnessFunction.TOOL_ENGINE,
            "count",
            new Threshold(Direction.valueOf(getDirection().get()), getLimit().get()),
            selectors,
            null,
            Map.of());
    getLogger()
        .lifecycle(
            "fitness: {}: value {}, {} {}",
            function.id(),
            measurement.value(),
            getDirection().get().toLowerCase(java.util.Locale.ROOT),
            getLimit().get());
    new Report(function, measurement)
        .failure()
        .ifPresent(
            message -> {
              throw new GradleException(message);
            });
  }

  protected final LastValues lastValues() {
    return new LastValues(getLastValues().get().getAsFile().toPath());
  }

  /** What a check reports instead of a value: who measures the function, or that an optional tool is skipped. */
  public static final class Noted extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Noted(String message) {
      super(message);
    }
  }
}

package fitness.gradle;

import fitness.core.Measurement;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/** A function only a run of its suite can measure: the tool enforces it there, this task names the suite and the number. */
public abstract class RunMeasured extends FitnessFunctionCheck {

  @Input
  public abstract Property<String> getMeasuredBy();

  @Override
  protected Measurement measure() {
    throw new Noted(
        "measured by "
            + getMeasuredBy().get()
            + ", "
            + getDirection().get().toLowerCase(java.util.Locale.ROOT)
            + " "
            + getLimit().get());
  }
}

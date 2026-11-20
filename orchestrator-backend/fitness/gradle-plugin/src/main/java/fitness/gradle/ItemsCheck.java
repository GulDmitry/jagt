package fitness.gradle;

import fitness.core.Measurement;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;

/** A function whose items the build already knows when it is configured. */
public abstract class ItemsCheck extends FitnessFunctionCheck {

  @Input
  public abstract ListProperty<String> getItems();

  @Override
  protected Measurement measure() {
    return Measurement.of(getItems().get());
  }
}

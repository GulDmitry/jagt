package technical.maintainability.analysability;

import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.List;

public final class FakeFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(List.of());
  }
}

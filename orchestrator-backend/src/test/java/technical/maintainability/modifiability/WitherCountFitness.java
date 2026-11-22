package technical.maintainability.modifiability;

import dev.jagt.orchestrator.RecordWithers;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.List;

public final class WitherCountFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return new Measurement(RecordWithers.all().count(), List.of());
  }
}

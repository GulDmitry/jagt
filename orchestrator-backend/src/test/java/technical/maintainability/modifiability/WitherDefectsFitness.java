package technical.maintainability.modifiability;

import dev.jagt.orchestrator.RecordWithers;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;

public final class WitherDefectsFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        RecordWithers.all()
            .map(RecordWithers::defect)
            .filter(defect -> !defect.isEmpty())
            .toList());
  }
}

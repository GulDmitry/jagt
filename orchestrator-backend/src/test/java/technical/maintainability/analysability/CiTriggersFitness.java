package technical.maintainability.analysability;

import dev.jagt.orchestrator.CiWorkflow;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.List;

public final class CiTriggersFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(List.copyOf(CiWorkflow.githubTriggers()));
  }
}

package technical.maintainability.testability;

import dev.jagt.orchestrator.FixedBugCommits;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;

public final class FixedBugCaseFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(FixedBugCommits.defects());
  }
}

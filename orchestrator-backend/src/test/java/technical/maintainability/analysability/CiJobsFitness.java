package technical.maintainability.analysability;

import dev.jagt.orchestrator.CiWorkflow;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.stream.Stream;

public final class CiJobsFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        Stream.concat(
                CiWorkflow.jobs().keySet().stream().map(job -> "github " + job),
                CiWorkflow.gitlabJobs().stream().map(job -> "gitlab " + job))
            .toList());
  }
}

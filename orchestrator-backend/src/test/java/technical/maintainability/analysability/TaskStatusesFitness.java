package technical.maintainability.analysability;

import com.tngtech.archunit.core.domain.JavaEnumConstant;
import dev.jagt.orchestrator.MainClasses;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;

public final class TaskStatusesFitness implements Fitness {

  private static final String TASK_STATUS = "dev.jagt.orchestrator.task.TaskStatus";

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        MainClasses.CODE.get(TASK_STATUS).getEnumConstants().stream()
            .map(JavaEnumConstant::name)
            .toList());
  }
}

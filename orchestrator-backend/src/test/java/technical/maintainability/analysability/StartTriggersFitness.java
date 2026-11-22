package technical.maintainability.analysability;

import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.stream.Stream;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

public final class StartTriggersFitness implements Fitness {

  private static final String JOB = "dev.jagt.orchestrator.job.Job";

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        Stream.concat(
                MainTypes.concrete(JOB).map(type -> "job " + type.getName()),
                MainTypes.methods()
                    .filter(method ->
                        method.isAnnotatedWith(EventListener.class)
                            || method.isAnnotatedWith(Scheduled.class))
                    .map(method -> "listener " + method.getFullName()))
            .toList());
  }
}

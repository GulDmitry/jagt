package technical.maintainability.testability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.LogCaptureConditions;
import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.stream.Stream;

public final class LogCaptureFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(
        Stream.of(
                ArchItems.of(
                    classes()
                        .should(LogCaptureConditions.holdNoLockAndNoSameThread())
                        .allowEmptyShould(true),
                    SourceRoots.ALL),
                ArchItems.of(
                    classes()
                        .should(LogCaptureConditions.buildNoListAppender())
                        .allowEmptyShould(true),
                    SourceRoots.ALL))
            .flatMap(found -> found.items().stream())
            .toList());
  }
}

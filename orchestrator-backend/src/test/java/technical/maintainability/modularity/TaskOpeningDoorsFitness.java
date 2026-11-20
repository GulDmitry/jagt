package technical.maintainability.modularity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.MainClasses;
import dev.jagt.orchestrator.NamedClasses;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class TaskOpeningDoorsFitness implements Fitness {

  private static final Path SOURCE =
      Path.of("src/test/java/technical/maintainability/modularity/TaskOpeningDoorsFitness.java");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return Measurement.of(Stream.concat(reachingTheLauncher(), namedClassesGone()).toList());
  }

  private static Stream<String> reachingTheLauncher() {
    return ArchItems.of(
        noClasses()
            .that()
            .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.service.TaskLauncher")
            .and()
            .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.command.DoCommand")
            .and()
            .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.command.ResumeCommand")
            .and()
            .doNotHaveFullyQualifiedName(
                "dev.jagt.orchestrator.surface.board.task.TaskCommandsController")
            .and()
            .doNotHaveFullyQualifiedName(
                "dev.jagt.orchestrator.surface.board.task.NaturalLanguageDispatch")
            .and()
            .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.job.tracker.IntakeStart")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("dev.jagt.orchestrator.service.TaskLauncher"),
        MainClasses.CODE)
        .items()
        .stream();
  }

  private static Stream<String> namedClassesGone() {
    return NamedClasses.in(SOURCE).stream()
        .filter(name -> !MainClasses.CODE.contain(name))
        .map(name -> "the door names a class that is gone: " + name);
  }
}

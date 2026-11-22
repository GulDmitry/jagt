package technical.maintainability.modularity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;
import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.CollaboratorOwners;
import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;

public final class CollaboratorOwnersFitness implements Fitness {

  public static ArchRule rule() {
    return classes()
        .that(CollaboratorOwners.areMainClasses())
        .should(CollaboratorOwners.buildNoCollaboratorOfTheirOwn())
        .because(
            "a collaborator arrives through the constructor; only one made of what the class holds,"
                + " a static factory, a @Bean method or a post-processor builds its own");
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return ArchItems.of(rule(), SourceRoots.ALL);
  }
}

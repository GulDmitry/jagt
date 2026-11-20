package technical.maintainability.modifiability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.ConstructorOwners;
import dev.jagt.orchestrator.MainClasses;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;

public final class ConstructorsCalledFromMainFitness implements Fitness {

  public static ArchRule rule() {
    return constructors()
        .that()
        .areNotPrivate()
        .and(ConstructorOwners.notCalledFromMain())
        .should(ArchCondition.from(ConstructorOwners.springOrRecordOwns()));
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return ArchItems.of(rule(), MainClasses.CODE);
  }
}

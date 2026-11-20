package technical.maintainability.modifiability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.MainClasses;
import dev.jagt.orchestrator.MainMembers;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;

public final class MembersCalledFromMainFitness implements Fitness {

  public static ArchRule rule() {
    return methods()
        .that(MainMembers.calledOnlyFromOutsideMain())
        .should(
            ArchCondition.from(
                DescribedPredicate.describe("be called from main", method -> false)));
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return ArchItems.of(FreezingArchRule.freeze(rule()), MainClasses.CODE);
  }
}

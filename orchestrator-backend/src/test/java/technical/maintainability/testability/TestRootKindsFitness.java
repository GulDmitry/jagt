package technical.maintainability.testability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.SourceRoots;
import dev.jagt.orchestrator.TestKinds;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;

public final class TestRootKindsFitness implements Fitness {

  public static ArchRule rule() {
    return classes()
        .that(TestKinds.areTopLevelInTestSet())
        .should(TestKinds.beTestBuilderFakeOrScanner());
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return ArchItems.of(FreezingArchRule.freeze(rule()), SourceRoots.ALL);
  }
}

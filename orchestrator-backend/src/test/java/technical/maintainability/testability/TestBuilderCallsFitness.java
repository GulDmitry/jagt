package technical.maintainability.testability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import dev.jagt.orchestrator.ArchItems;
import dev.jagt.orchestrator.SourceRoots;
import dev.jagt.orchestrator.TestKinds;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;

public final class TestBuilderCallsFitness implements Fitness {

  public static ArchRule rule() {
    return noClasses()
        .that(TestKinds.testBuilders())
        .should()
        .callMethodWhere(TestKinds.readClockRandomMockOrAssertion());
  }

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    return ArchItems.of(rule(), SourceRoots.ALL);
  }
}

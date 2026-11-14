package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class NoHelperMethodTest {

  @Test
  void testClassDeclaresNoMethodButTestsSetupAndArguments() {
    FreezingArchRule.freeze(
            methods()
                .that()
                .areDeclaredInClassesThat(TestKinds.declaredInTestClass())
                .should(TestKinds.beTestSetupOrArguments()))
        .check(SourceRoots.ALL);
  }
}

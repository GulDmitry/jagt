package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class TestNamingTest {

  @Test
  void unitTestIsNamedAfterMainClassOfItsPackage() {
    FreezingArchRule.freeze(
            classes()
                .that(TestKinds.moduleUnitTests())
                .should(TestKinds.beNamedAfterMainClassOfItsPackage()))
        .check(SourceRoots.ALL);
  }
}

package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class TestRootKindsTest {

  @Test
  void testRootHoldsOnlyTestsBuildersFakesAndScanners() {
    FreezingArchRule.freeze(
            classes()
                .that(TestKinds.areTopLevelInTestSet())
                .should(TestKinds.beTestBuilderFakeOrScanner()))
        .check(SourceRoots.ALL);
  }
}

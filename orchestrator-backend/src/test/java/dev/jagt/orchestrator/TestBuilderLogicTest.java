package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class TestBuilderLogicTest {

  @Test
  void builderReadsNoClockRandomAndCallsNoMockOrAssertion() {
    noClasses()
        .that(TestKinds.testBuilders())
        .should()
        .callMethodWhere(TestKinds.readClockRandomMockOrAssertion())
        .check(SourceRoots.ALL);
  }

  @Test
  void builderWiresOneServiceAtMost() {
    FreezingArchRule.freeze(
            classes().that(TestKinds.testBuilders()).should(TestKinds.wireOneComponentAtMost()))
        .check(SourceRoots.ALL);
  }

  @Test
  void builderHoldsNoBranchOrLoop() {
    assertThat(TestKinds.testBuilderSources().filter(TestKinds::holdsBranchOrLoop)).isEmpty();
  }
}

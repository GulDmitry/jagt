package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

class TestBuilderLogicTest {

  @Test
  void builderReadsNoClockRandomAndCallsNoMockOrAssertion() {
    FreezingArchRule.freeze(
            noClasses()
                .that(TestKinds.testBuilders())
                .should()
                .callMethodWhere(TestKinds.readClockRandomMockOrAssertion()))
        .check(SourceRoots.ALL);
  }

  @Test
  void builderHoldsNoBranchOrLoop() {
    assertThat(TestKinds.testBuilderSources().filter(TestKinds::holdsBranchOrLoop)).isEmpty();
  }
}

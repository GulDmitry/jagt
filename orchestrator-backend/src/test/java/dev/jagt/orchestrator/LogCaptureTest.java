package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.Test;

class LogCaptureTest {

  @Test
  void noTestHoldsLockOrRunsOnOneThread() {
    classes()
        .should(LogCaptureConditions.holdNoLockAndNoSameThread())
        .allowEmptyShould(true)
        .check(SourceRoots.ALL);
  }

  @Test
  void logIsCapturedOnlyThroughTheFake() {
    classes()
        .should(LogCaptureConditions.buildNoListAppender())
        .allowEmptyShould(true)
        .check(SourceRoots.ALL);
  }
}

package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class TaskOpeningDoorsTest {

  @Test
  void aTaskOpensOnlyThroughTheHumansCommandsAndTrackerIntake() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.service.TaskLauncher")
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.command.DoCommand")
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.command.ResumeCommand")
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.surface.board.TaskCommandsController")
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.surface.board.NaturalLanguageDispatch")
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.job.IntakeStart")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("dev.jagt.orchestrator.service.TaskLauncher")
        .check(MainClasses.CODE);
  }
}

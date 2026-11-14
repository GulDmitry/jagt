package dev.jagt.orchestrator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class TaskOpeningDoorsTest {

  @Test
  void taskOpensOnlyThroughTheHumansCommandsAndTrackerIntake() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.service.TaskLauncher")
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.command.DoCommand")
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.command.ResumeCommand")
        .and()
        .doNotHaveFullyQualifiedName(
            "dev.jagt.orchestrator.surface.board.task.TaskCommandsController")
        .and()
        .doNotHaveFullyQualifiedName(
            "dev.jagt.orchestrator.surface.board.task.NaturalLanguageDispatch")
        .and()
        .doNotHaveFullyQualifiedName("dev.jagt.orchestrator.job.tracker.IntakeStart")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("dev.jagt.orchestrator.service.TaskLauncher")
        .check(MainClasses.CODE);
  }
}

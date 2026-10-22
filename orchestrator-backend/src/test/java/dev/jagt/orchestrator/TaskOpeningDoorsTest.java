package dev.jagt.orchestrator;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class TaskOpeningDoorsTest {

    @Test
    void aTaskOpensOnlyThroughTheHumansCommandsAndTrackerIntake() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.service.TaskLauncher")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.command.DoCommand")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.command.ResumeCommand")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.surface.board.TaskCommandsController")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.surface.board.NaturalLanguageDispatch")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.job.IntakeStart")
                .should().dependOnClassesThat().haveFullyQualifiedName("dev.jagt.orchestrator.service.TaskLauncher")
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }
}

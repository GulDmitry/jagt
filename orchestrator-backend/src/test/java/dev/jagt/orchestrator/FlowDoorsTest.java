package dev.jagt.orchestrator;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import dev.jagt.orchestrator.task.Turn;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class FlowDoorsTest {

    @Test
    void aStatusIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withStatus")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aStopIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withStop")))
                .orShould().callMethodWhere(target(name("lifted")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aPlanGateIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withPlanGate")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aReportIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withReport")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aTurnIsOpenedAndClosedOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withTurn")))
                .orShould().callMethod(Turn.class, "relayed", String.class, String.class, String.class, long.class)
                .orShould().callMethod(Turn.class, "closed", long.class)
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aDeploymentIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withDeployment")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aSessionIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withSession")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aStatusIsRestoredOnlyByTheClaimUndo() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.FlowReports")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("restoredFrom")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }
}

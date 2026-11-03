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
    void aGitTrustIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withGitTrust")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aRepositoryRequestIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withRepoRequest")))
                .orShould().callMethodWhere(target(name("withRepo")))
                .orShould().callMethodWhere(target(name("withoutRepo")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void aReviewRequestIsLinkedAndMergedOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState$TaskStateBuilder")
                .should().callMethodWhere(target(name("withMrUrl")))
                .orShould().callMethodWhere(target(name("withMrUrls")))
                .orShould().callMethodWhere(target(name("withReviewRound")))
                .orShould().callMethodWhere(target(name("withMerged")))
                .orShould().callMethodWhere(target(name("withMergedByHost")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void theChecksTheApprovalAndTheHeadAreWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withChecksRead")))
                .orShould().callMethodWhere(target(name("withChecksUnread")))
                .orShould().callMethodWhere(target(name("withChecksReset")))
                .orShould().callMethodWhere(target(name("withApproved")))
                .orShould().callMethodWhere(target(name("withHead")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void whatARoleLeftOnATaskIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethod(dev.jagt.orchestrator.task.TaskState.class, "withRole", String.class, dev.jagt.orchestrator.task.RoleRecord.class)
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void whatTheMasterLeftOnATaskIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethod(dev.jagt.orchestrator.task.TaskState.class, "withMaster",
                        dev.jagt.orchestrator.task.Master.class)
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }

    @Test
    void anInfraRetryIsWrittenOnlyByTheReducer() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.TaskReducer")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("withInfraRetries")))
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
    void aSessionSignIsStampedOnlyByTheSessionRecordItself() {
        noClasses().that().resideOutsideOfPackage("dev.jagt.orchestrator.flow..")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.Session")
                .should().callMethodWhere(target(name("withHalt")))
                .orShould().callMethodWhere(target(name("withQuietSince")))
                .orShould().callMethodWhere(target(name("withMovedAt")))
                .orShould().callMethodWhere(target(name("withWorkStartedAt")))
                .orShould().callMethodWhere(target(name("withReportedAt")))
                .orShould().callMethodWhere(target(name("withStampedAsk")))
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

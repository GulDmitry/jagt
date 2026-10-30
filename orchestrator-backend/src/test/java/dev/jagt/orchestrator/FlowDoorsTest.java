package dev.jagt.orchestrator;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

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
    void aStatusIsRestoredOnlyByTheClaimUndo() {
        noClasses().that().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.flow.FlowReports")
                .and().doNotHaveFullyQualifiedName("dev.jagt.orchestrator.task.TaskState")
                .should().callMethodWhere(target(name("restoredFrom")))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }
}

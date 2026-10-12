package dev.jagt.orchestrator;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

class CollaboratorCeilingTest {

    @Test
    void aComponentTakesFiveCollaboratorsAtMost() {
        constructors().that().areDeclaredInClassesThat().areMetaAnnotatedWith(Component.class)
                .should().haveRawParameterTypes(DescribedPredicate.describe("five at most", types -> types.size() <= 5))
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("dev.jagt.orchestrator"));
    }
}

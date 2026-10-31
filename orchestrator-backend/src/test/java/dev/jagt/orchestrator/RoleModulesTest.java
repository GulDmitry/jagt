package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleModulesTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.jagt.orchestrator");

    @Test
    void everyClassNamedForTheArbiterSitsInItsDeclaredModule() {
        assertThat(CODE.stream().filter(JavaClass::isTopLevelClass)
                .filter(type -> type.getSimpleName().startsWith("Master") || type.getSimpleName().startsWith("Arbiter"))
                .map(JavaClass::getName).filter(name -> !RoleModules.inArbiter(name))).isEmpty();
    }

    @Test
    void theDeclaredArbiterModuleNamesNoPackageOrClassThatIsGone() {
        assertThat(RoleModules.ARBITER.stream().filter(prefix -> CODE.stream().noneMatch(type -> type.getName().startsWith(prefix))))
                .isEmpty();
    }
}

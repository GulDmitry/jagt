package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RoleModulesTest {

    private static final JavaClasses CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.jagt.orchestrator");

    @Test
    void theDeclaredModulesNameNoPackageOrClassThatIsGone() {
        assertThat(Stream.concat(RoleModules.ARBITER.stream(), RoleModules.CHECKERS.values().stream().flatMap(java.util.List::stream))
                .filter(prefix -> CODE.stream().noneMatch(type -> type.getName().startsWith(prefix)))).isEmpty();
    }
}

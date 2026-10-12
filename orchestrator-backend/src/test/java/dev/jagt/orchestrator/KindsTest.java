package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KindsTest {

    private static final JavaClasses CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.jagt.orchestrator");
    private static final Set<String> USE_CASES = Set.of("capability", "command", "job", "notify", "startup", "service");
    private static final Set<String> FACES = Set.of("Jobs", "Notifications");

    @Test
    void aServiceClassOneKindAloneReadsLivesInThatKind() {
        assertThat(topLevel().filter(type -> kind(type).equals("service"))
                .filter(type -> {
                    Set<String> readers = readers(type);
                    return readers.size() == 1 && !readers.contains("service") && !readers.contains("surface");
                })
                .map(type -> type.getSimpleName() + " " + readers(type))).isEmpty();
    }

    @Test
    void anotherUseCaseReadsAKindOnlyThroughItsRegistry() {
        assertThat(topLevel().filter(type -> USE_CASES.contains(kind(type)) && !kind(type).equals("service"))
                .filter(type -> !FACES.contains(type.getSimpleName()))
                .filter(type -> readers(type).stream().map(reader -> reader.split("/")[0])
                        .anyMatch(reader -> USE_CASES.contains(reader) && !reader.equals(kind(type))))
                .map(type -> type.getSimpleName() + " " + readers(type))).isEmpty();
    }

    @Test
    void readsEveryKindItJudges() {
        assertThat(topLevel().map(KindsTest::kind).collect(Collectors.toSet()))
                .containsAll(USE_CASES).contains("service/master", "surface");
    }

    private static Stream<JavaClass> topLevel() {
        return CODE.stream().filter(JavaClass::isTopLevelClass);
    }

    private static Set<String> readers(JavaClass type) {
        return CODE.stream().filter(own -> isPartOf(own, type))
                .flatMap(own -> own.getDirectDependenciesToSelf().stream()).map(Dependency::getOriginClass)
                .filter(origin -> !isPartOf(origin, type)).map(KindsTest::kind)
                .filter(kind -> !kind.equals("root")).collect(Collectors.toSet());
    }

    private static boolean isPartOf(JavaClass candidate, JavaClass type) {
        return candidate.getName().equals(type.getName()) || candidate.getName().startsWith(type.getName() + "$");
    }

    private static String kind(JavaClass type) {
        String[] packages = (type.getPackageName() + ".").replaceFirst("^dev\\.jagt\\.orchestrator\\.?", "").split("\\.");
        if (packages[0].isEmpty()) {
            return "root";
        }
        return packages[0].equals("service") && packages.length > 1 ? "service/" + packages[1] : packages[0];
    }
}

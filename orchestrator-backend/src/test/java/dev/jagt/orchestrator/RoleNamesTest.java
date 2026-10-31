package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RoleNamesTest {

    private static final JavaClasses CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.jagt.orchestrator");

    private static final Map<String, String> ALLOWED = Map.of(
            "dev.jagt.orchestrator.service.ConfigService$ConfigFile$QaConfig", "the old orchestrator.qa key, named as jagt.yml wrote it",
            "dev.jagt.orchestrator.service.ConfigService$ConfigFile$QaEnvironment", "an environment as roles.qa.environments writes it",
            "dev.jagt.orchestrator.service.ConfigService$ConfigFile$MasterConfig", "the old orchestrator.master key, named as jagt.yml wrote it",
            "dev.jagt.orchestrator.flow.TaskMove$MasterNoted", "a move of the arbiter's region, in the chart's one closed set of moves");

    @Test
    void noClassOutsideARolesModuleNamesIt() {
        List<String> named = Stream.concat(installedIds().map(RoleNamesTest::capitalised), Stream.of("Master", "Arbiter")).toList();

        assertThat(CODE.stream().filter(type -> named.stream().anyMatch(type.getSimpleName()::startsWith))
                .map(JavaClass::getName).filter(name -> !RoleModules.inAModule(name))
                .filter(name -> !ALLOWED.containsKey(name))).isEmpty();
    }

    @Test
    void anAllowedNameIsAClassThatStillExists() {
        assertThat(ALLOWED.keySet().stream().filter(name -> CODE.stream().noneMatch(type -> type.getName().equals(name)))).isEmpty();
    }

    private static Stream<String> installedIds() {
        try (Stream<Path> folders = Files.list(Path.of("..", "roles"))) {
            return folders.filter(folder -> Files.exists(folder.resolve("role.yml.dist")))
                    .map(folder -> folder.getFileName().toString()).toList().stream();
        } catch (IOException unread) {
            throw new UncheckedIOException(unread);
        }
    }

    private static String capitalised(String id) {
        return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
    }
}

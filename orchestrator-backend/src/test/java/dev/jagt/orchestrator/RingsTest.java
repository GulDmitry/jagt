package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

class RingsTest {

    private static final JavaClasses CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("dev.jagt.orchestrator");

    private static final List<Path> ROOTS = List.of(
            Path.of("core/src/main/java/dev/jagt/orchestrator"),
            Path.of("usecase/src/main/java/dev/jagt/orchestrator"),
            Path.of("adapter/src/main/java/dev/jagt/orchestrator"),
            Path.of("surface/src/main/java/dev/jagt/orchestrator"),
            Path.of("src/main/java/dev/jagt/orchestrator"));

    @ParameterizedTest
    @CsvSource({"task, task", "port, task port", "flow, task port flow", "protocol, task port flow protocol"})
    void aCentreRingDependsOnlyOnTheRingsItMaySee(String ring, String maySee) {
        classes().that().resideInAPackage(packageOf(ring)).should().onlyDependOnClassesThat(
                        resideOutsideOfPackage("dev.jagt.orchestrator..")
                                .or(resideInAnyPackage(Stream.of(maySee.split(" ")).map(RingsTest::packageOf)
                                        .toArray(String[]::new))))
                .check(CODE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"capability", "command", "job", "notify", "service", "surface", "config", "startup"})
    void nothingBetweenTheCentreAndTheEdgeNamesTheEdge(String ring) {
        noClasses().that().resideInAPackage(packageOf(ring)).should().dependOnClassesThat()
                .resideInAPackage(packageOf("adapter")).check(CODE);
    }

    @Test
    void noTwoRingsDependOnEachOther() {
        slices().matching("dev.jagt.orchestrator.(*)..").should().beFreeOfCycles().check(CODE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"flow", "task", "port", "protocol"})
    void theCentreCarriesNoSpring(String ring) {
        assertThat(sources(ring).filter(text -> text.contains("org.springframework"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"statechart, core usecase adapter surface .", "core, usecase adapter surface .",
            "usecase, adapter surface ."})
    void aModuleNamesNoTypeOfTheModulesAroundItEvenInAComment(String inner, String outer) {
        Set<String> outerTypes = Stream.of(outer.split(" "))
                .flatMap(module -> javaFilesUnder(Path.of(module, "src/main/java")))
                .map(path -> path.getFileName().toString().replace(".java", "")).collect(toSet());

        assertThat(javaFilesUnder(Path.of(inner, "src/main/java"))
                .filter(path -> Pattern.compile("\\b[A-Z]\\w+").matcher(read(path)).results()
                        .map(MatchResult::group).anyMatch(outerTypes::contains))
                .map(Path::getFileName)).isEmpty();
    }

    @Test
    void theStatechartImportsNothingButTheJdk() {
        assertThat(javaFilesUnder(Path.of("statechart", "src/main/java"))
                .filter(path -> Pattern.compile("^import (?:static )?(?!java\\.|dev\\.jagt\\.statechart\\.)",
                        Pattern.MULTILINE).matcher(read(path)).find())
                .map(Path::getFileName)).isEmpty();
    }

    @Test
    void readsEveryRingItClaimsToCheck() {
        assertThat(ROOTS).allSatisfy(root -> assertThat(Files.isDirectory(root))
                .describedAs("source root %s", root).isTrue());
        assertThat(List.of("flow", "task", "port", "protocol", "capability", "command", "job", "notify",
                "service", "surface", "adapter", "config", "startup")).allSatisfy(ring ->
                assertThat(sources(ring).count()).describedAs("java files in %s", ring).isPositive());
    }

    @Test
    void theOperatingSystemIsNamedOnlyAtTheEdge() {
        List<String> osOnly = List.of("osascript", "notify-send", "setsid", "/opt/homebrew", "/usr/local/bin",
                "wt.exe", "PATHEXT", "lsof", "kill -9", "/Applications");

        assertThat(ROOTS.stream().flatMap(RingsTest::javaFilesUnder)
                .filter(path -> !path.toString().contains("/adapter/"))
                .filter(path -> osOnly.stream().anyMatch(name -> read(path).contains(name)))
                .map(Path::getFileName)).isEmpty();
    }

    @Test
    void theVendorIsNamedOnlyAtTheEdge() {
        List<String> vendors = List.of("claude", "codex", "kitty", "tmux", "intellij", "jetbrains", "jira", "gitlab",
                "github", "anthropic", "openai", "mcp__");
        Pattern keysNamingAVendorByDesign = Pattern.compile("tmuxCommand|tmuxSession");

        assertThat(ROOTS.stream().flatMap(RingsTest::javaFilesUnder)
                .filter(path -> !path.toString().contains("/adapter/"))
                .filter(path -> vendors.stream().anyMatch(keysNamingAVendorByDesign.matcher(read(path))
                        .replaceAll("").toLowerCase(Locale.ROOT)::contains))
                .map(Path::getFileName)).isEmpty();
    }

    @Test
    void anIdesFolderIsNamedOnlyWhereAWorktreeIsFilled() {
        List<String> ideFolders = List.of(".idea", ".vscode", ".fleet");
        List<String> worktreeFilling = List.of("WorktreeNoise.java", "WorktreeFiles.java");

        assertThat(ROOTS.stream().flatMap(RingsTest::javaFilesUnder)
                .filter(path -> !path.toString().contains("/adapter/"))
                .filter(path -> !worktreeFilling.contains(path.getFileName().toString()))
                .filter(path -> ideFolders.stream().anyMatch(read(path)::contains))
                .map(Path::getFileName)).isEmpty();
    }

    private static String packageOf(String ring) {
        return "dev.jagt.orchestrator." + ring + "..";
    }

    private static Stream<String> sources(String ring) {
        return ROOTS.stream().map(root -> root.resolve(ring)).filter(Files::isDirectory)
                .flatMap(RingsTest::javaFilesUnder).map(RingsTest::read);
    }

    private static Stream<Path> javaFilesUnder(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

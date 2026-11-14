package dev.jagt.orchestrator;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RingsTest {

  @ParameterizedTest
  @CsvSource({
    "task, task",
    "port, task port",
    "flow, task port flow",
    "protocol, task port flow protocol"
  })
  void centreRingDependsOnlyOnTheRingsItMaySee(String ring, String maySee) {
    classes()
        .that()
        .resideInAPackage(RingFiles.packageOf(ring))
        .should()
        .onlyDependOnClassesThat(
            resideOutsideOfPackage("dev.jagt.orchestrator..")
                .or(
                    resideInAnyPackage(
                        Stream.of(maySee.split(" "))
                            .map(RingFiles::packageOf)
                            .toArray(String[]::new))))
        .check(MainClasses.CODE);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "capability",
        "command",
        "job",
        "notify",
        "service",
        "surface",
        "config",
        "startup"
      })
  void nothingBetweenTheCentreAndTheEdgeNamesTheEdge(String ring) {
    noClasses()
        .that()
        .resideInAPackage(RingFiles.packageOf(ring))
        .should()
        .dependOnClassesThat()
        .resideInAPackage(RingFiles.packageOf("adapter"))
        .check(MainClasses.CODE);
  }

  @Test
  void noTwoRingsDependOnEachOther() {
    slices()
        .matching("dev.jagt.orchestrator.(*)..")
        .should()
        .beFreeOfCycles()
        .check(MainClasses.CODE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"flow", "task", "port", "protocol"})
  void theCentreCarriesNoSpring(String ring) {
    assertThat(RingFiles.sources(ring).filter(text -> text.contains("org.springframework")))
        .isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "statechart, core usecase adapter surface .",
    "core, usecase adapter surface .",
    "usecase, adapter surface ."
  })
  void moduleNamesNoTypeOfTheModulesAroundItEvenInComment(String inner, String outer) {
    Set<String> outerTypes =
        Stream.of(outer.split(" "))
            .flatMap(module -> RingFiles.inModule(module, true))
            .map(path -> path.getFileName().toString().replace(".java", ""))
            .collect(toSet());

    assertThat(
            RingFiles.inModule(inner, false)
                .filter(path ->
                    Pattern.compile("\\b[A-Z]\\w+")
                        .matcher(SourceRoots.read(path))
                        .results()
                        .map(MatchResult::group)
                        .anyMatch(outerTypes::contains))
                .map(Path::getFileName))
        .isEmpty();
  }

  @Test
  void theStatechartImportsNothingButTheJdk() {
    assertThat(
            RingFiles.inModule("statechart", true)
                .filter(path ->
                    Pattern.compile(
                            "^import (?:static )?"
                                + "(?!java\\.|dev\\.jagt\\.statechart\\.|org\\.jspecify\\.)",
                            Pattern.MULTILINE)
                        .matcher(SourceRoots.read(path))
                        .find())
                .map(Path::getFileName))
        .isEmpty();
  }

  @Test
  void readsEveryRingItClaimsToCheck() {
    assertThat(RingFiles.ROOTS)
        .allSatisfy(root ->
            assertThat(Files.isDirectory(root)).describedAs("source root %s", root).isTrue());
    assertThat(
            List.of(
                "flow",
                "task",
                "port",
                "protocol",
                "capability",
                "command",
                "job",
                "notify",
                "service",
                "surface",
                "adapter",
                "config",
                "startup"))
        .allSatisfy(ring ->
            assertThat(RingFiles.sources(ring).count())
                .describedAs("java files in %s", ring)
                .isPositive());
  }

  @Test
  void theOperatingSystemIsNamedOnlyAtTheEdge() {
    List<String> osOnly =
        List.of(
            "osascript",
            "notify-send",
            "setsid",
            "/opt/homebrew",
            "/usr/local/bin",
            "wt.exe",
            "PATHEXT",
            "lsof",
            "kill -9",
            "/Applications");

    assertThat(
            RingFiles.outsideTheEdge(true)
                .filter(
                    path -> osOnly.stream().anyMatch(name -> SourceRoots.read(path).contains(name)))
                .map(Path::getFileName))
        .isEmpty();
  }

  @Test
  void theVendorIsNamedOnlyAtTheEdge() {
    List<String> vendors =
        List.of(
            "claude",
            "codex",
            "kitty",
            "tmux",
            "intellij",
            "jetbrains",
            "jira",
            "gitlab",
            "github",
            "anthropic",
            "openai",
            "mcp__");
    Pattern keysNamingVendorByDesign = Pattern.compile("tmuxCommand|tmuxSession|tmuxSocket");

    assertThat(
            RingFiles.outsideTheEdge(false)
                .filter(path ->
                    vendors.stream()
                        .anyMatch(
                            keysNamingVendorByDesign
                                    .matcher(SourceRoots.read(path))
                                    .replaceAll("")
                                    .toLowerCase(Locale.ROOT)
                                ::contains))
                .map(Path::getFileName))
        .isEmpty();
  }

  @Test
  void anIdesFolderIsNamedOnlyWhereWorktreeIsFilled() {
    List<String> ideFolders = List.of(".idea", ".vscode", ".fleet");
    List<String> worktreeFilling = List.of("WorktreeNoise.java", "WorktreeFiles.java");

    assertThat(
            RingFiles.outsideTheEdge(false)
                .filter(path -> !worktreeFilling.contains(path.getFileName().toString()))
                .filter(path -> ideFolders.stream().anyMatch(SourceRoots.read(path)::contains))
                .map(Path::getFileName))
        .isEmpty();
  }
}

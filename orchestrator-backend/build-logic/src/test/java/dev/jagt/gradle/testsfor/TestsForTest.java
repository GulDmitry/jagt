package dev.jagt.gradle.testsfor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TestsForTest {

  private static final String ROOT_TESTS =
      "orchestrator-backend/src/test/java/dev/jagt/orchestrator/";
  private static final String ADAPTER = "orchestrator-backend/adapter/src/";
  private static final String FITNESS_YML = "fitness.yml";
  private static final String FITNESS_YML_TEXT =
      """
      technical:
        maintainability:
          analysability:
            page-lines: { engine: scan, glob: "../AGENTS.md,../openspec/specs/*/spec.md", governs: size, max: 200 }
            rule-lines: { engine: scan, glob: "../docs/rules/*.md", governs: size, max: 100 }
            decision-lines: { engine: scan, glob: "../docs/decisions/????-*.md", governs: size, max: 50 }
        security:
          integrity:
            other-lines: { engine: scan, glob: "../notes/*.md", governs: size, max: 5 }
      """;
  private static final String GRADLE = "cd orchestrator-backend && timeout ";

  @Test
  void printsCompileCheckstyleTouchedTestsThenRuleTestsInOrderWhenMainClassChanges(
      @TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(ADAPTER + "main/java/a/Foo.java", "class Foo {}")
            .file(ADAPTER + "test/java/a/FooTest.java", "class FooTest {}")
            .file(ROOT_TESTS + "PackagesTest.java", "class PackagesTest {}")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of(ADAPTER + "main/java/a/Foo.java"));

    assertThat(plan.lines())
        .containsExactly(
            GRADLE + "600 ./gradlew :adapter:compileJava :adapter:compileTestJava",
            GRADLE + "600 ./gradlew :adapter:checkstyleMain :adapter:checkstyleTest",
            GRADLE + "900 ./gradlew :adapter:test --tests '*FooTest' -x pitestDiff",
            GRADLE + "900 ./gradlew :test --tests '*PackagesTest' -x pitestDiff");
  }

  @Test
  void runsThePropertyTestTaskNamingTheJqwikClassWhenMainClassChanges(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("orchestrator-backend/core/src/main/java/c/Reducer.java", "class Reducer {}")
            .file(
                "orchestrator-backend/core/src/test/java/c/ReducerProperties.java",
                "import net.jqwik.api.Property;\nclass ReducerProperties {}")
            .tracked();

    TestsFor.Plan plan =
        testsFor.plan(List.of("orchestrator-backend/core/src/main/java/c/Reducer.java"));

    assertThat(plan.lines())
        .contains(
            GRADLE
                + "900 ./gradlew :core:propertyTest --tests '*ReducerProperties' -x pitestDiff")
        .noneMatch(line -> line.contains(":core:test "));
  }

  @Test
  void runsThePropertyTestTaskWhenTheJqwikClassItselfChanges(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(
                "orchestrator-backend/core/src/test/java/c/ReducerProperties.java",
                "import net.jqwik.api.Property;\nclass ReducerProperties {}")
            .tracked();

    TestsFor.Plan plan =
        testsFor.plan(
            List.of("orchestrator-backend/core/src/test/java/c/ReducerProperties.java"));

    assertThat(plan.lines())
        .contains(
            GRADLE
                + "900 ./gradlew :core:propertyTest --tests '*ReducerProperties' -x pitestDiff")
        .noneMatch(line -> line.contains(":core:test "));
  }

  @Test
  void selectsTestOfDependentModuleThatNamesTheChangedClass(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("orchestrator-backend/core/src/main/java/c/Ticket.java", "class Ticket {}")
            .file(
                "orchestrator-backend/usecase/src/test/java/u/BoardTest.java",
                "class BoardTest { Ticket t; }")
            .tracked();

    TestsFor.Plan plan =
        testsFor.plan(List.of("orchestrator-backend/core/src/main/java/c/Ticket.java"));

    assertThat(plan.lines())
        .contains(GRADLE + "900 ./gradlew :usecase:test --tests '*BoardTest' -x pitestDiff");
  }

  @Test
  void runsTheDependentsTomcatTestNamingNoClassWhenMainClassChanges(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("orchestrator-backend/surface/src/main/java/s/Doors.java", "class Doors {}")
            .file(ROOT_TESTS + "BoardOnTomcatTest.java", "class BoardOnTomcatTest {}")
            .tracked();

    TestsFor.Plan plan =
        testsFor.plan(List.of("orchestrator-backend/surface/src/main/java/s/Doors.java"));

    assertThat(plan.lines())
        .contains(GRADLE + "900 ./gradlew :test --tests '*BoardOnTomcatTest' -x pitestDiff");
  }

  @Test
  void compilesEverySourceSetWhenMainClassIsRemoved(@TempDir Path repo) throws Exception {
    TestsFor testsFor = TestRepoBuilder.in(repo).file(ROOT_TESTS + "X.txt", "x").tracked();

    TestsFor.Plan plan =
        testsFor.plan(List.of("orchestrator-backend/surface/src/main/java/s/Gone.java"));

    assertThat(String.join("\n", plan.lines()))
        .contains(":surface:compileJava")
        .contains(":compileE2eJava")
        .contains(":stand:compileStandJava");
  }

  @Test
  void plansOnlyTheSuccessorsTasksWhenSourceSetIsRenamed(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("orchestrator-backend/src/newEval/java/e/CaseTest.java", "class CaseTest {}")
            .tracked();

    TestsFor.Plan plan =
        testsFor.plan(
            List.of(
                "orchestrator-backend/src/oldEval/java/e/CaseTest.java",
                "orchestrator-backend/src/newEval/java/e/CaseTest.java"));

    assertThat(plan.lines())
        .startsWith(
            GRADLE + "600 ./gradlew :compileJava :compileTestJava :compileNewEvalJava",
            GRADLE + "600 ./gradlew :checkstyleMain :checkstyleTest :checkstyleNewEval");
  }

  @Test
  void runsOnlyTheDocumentRuleTestsThatExistWhenMarkdownChanges(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(ROOT_TESTS + "FitnessTest.java", "class FitnessTest {}")
            .file("docs/a.md", "# a")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of("docs/a.md"));

    assertThat(plan.dropped()).contains("tests-for: no rule test DocLinksTest, dropped");
    assertThat(plan.lines())
        .containsExactly(GRADLE + "900 ./gradlew :test --tests '*FitnessTest' -x pitestDiff");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "openspec/specs/board/spec.md",
        "docs/rules/ci.md",
        "docs/decisions/0001-a.md",
        "AGENTS.md"
      })
  void runsTheMaintainabilitySizeRowsWhenBudgetedMarkdownChanges(String changed, @TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(FITNESS_YML, FITNESS_YML_TEXT)
            .file(ROOT_TESTS + "SpecLimitsTest.java", "class SpecLimitsTest {}")
            .file(changed, "# a")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of(changed));

    assertThat(plan.lines())
        .containsExactly(
            GRADLE + "900 ./gradlew :test --tests '*SpecLimitsTest' -x pitestDiff",
            GRADLE + "600 ./gradlew fitness -Pfitness.block=technical.maintainability");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "notes/a.md",
        "docs/decisions/001-a.md",
        "docs/decisions/00001-a.md",
        "docs/x/y.md"
      })
  void runsNoSizeRowsWhenMarkdownIsOutsideEverySizeRow(String changed, @TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo).file(FITNESS_YML, FITNESS_YML_TEXT).file(changed, "# a").tracked();

    TestsFor.Plan plan = testsFor.plan(List.of(changed));

    assertThat(plan.lines()).noneMatch(line -> line.contains("technical.maintainability"));
  }

  @Test
  void runsTheRuleTestScanningTheModuleWhenItsMainSourceChanges(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(ADAPTER + "main/java/a/Foo.java", "class Foo {}")
            .file(
                ROOT_TESTS + "AdapterPorts.java",
                "class AdapterPorts { Path p = Path.of(\"adapter/src/main/java\"); }")
            .file(
                ROOT_TESTS + "SeamSelectionTest.java",
                "class SeamSelectionTest { AdapterPorts a; }")
            .file(ROOT_TESTS + "UnrelatedTest.java", "class UnrelatedTest {}")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of(ADAPTER + "main/java/a/Foo.java"));

    assertThat(plan.lines())
        .contains(GRADLE + "900 ./gradlew :test --tests '*SeamSelectionTest' -x pitestDiff")
        .noneMatch(line -> line.contains("UnrelatedTest"));
  }

  @Test
  void runsNoScanningRuleTestWhenAnotherModuleChanges(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("orchestrator-backend/core/src/main/java/c/Foo.java", "class Foo {}")
            .file(
                ROOT_TESTS + "AdapterPorts.java",
                "class AdapterPorts { Path p = Path.of(\"adapter/src/main/java\"); }")
            .file(
                ROOT_TESTS + "SeamSelectionTest.java",
                "class SeamSelectionTest { AdapterPorts a; }")
            .tracked();

    TestsFor.Plan plan =
        testsFor.plan(List.of("orchestrator-backend/core/src/main/java/c/Foo.java"));

    assertThat(plan.lines()).noneMatch(line -> line.contains("SeamSelectionTest"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"docs/decisions/0001-a.md", ".github/workflows/ci.yml"})
  void runsTheFitnessTestWhenFileInScanRowGlobChanges(String changed, @TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(
                FITNESS_YML,
                """
                technical:
                  security:
                    ci-rule: { engine: scan, glob: "../.github/workflows/ci.yml,../docs/decisions/*.md", regex: "x", governs: count, max: 0 }
                    brace-rule: { engine: scan, glob: "**/*.{gradle,yml}", regex: "x", governs: count, max: 0 }
                """)
            .file(ROOT_TESTS + "FitnessTest.java", "class FitnessTest {}")
            .file(changed, "x")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of(changed));

    assertThat(plan.lines())
        .contains(GRADLE + "900 ./gradlew :test --tests '*FitnessTest' -x pitestDiff");
  }

  @Test
  void runsNoFitnessTestWhenFileMatchesNoScanRowGlob(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(
                FITNESS_YML,
                """
                technical:
                  security:
                    ci-rule: { engine: scan, glob: "../.github/workflows/ci.yml", regex: "x", governs: count, max: 0 }
                """)
            .file(ROOT_TESTS + "FitnessTest.java", "class FitnessTest {}")
            .file(".github/workflows/other.yml", "x")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of(".github/workflows/other.yml"));

    assertThat(plan.lines()).noneMatch(line -> line.contains("FitnessTest"));
  }

  @Test
  void ruleTestAlsoNamingTheChangedWorkflowRunsOnceWhenWorkflowChanges(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(
                ROOT_TESTS + "CiWorkflowTest.java",
                "class CiWorkflowTest { String f = \"ci.yml\"; }")
            .file(".github/workflows/ci.yml", "on: push")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of(".github/workflows/ci.yml"));

    assertThat(plan.lines())
        .containsExactly(GRADLE + "900 ./gradlew :test --tests '*CiWorkflowTest' -x pitestDiff");
  }

  @ParameterizedTest
  @CsvSource({
    "app.server.yml.dist, app.server.yml.dist",
    "specs/master/spec.md, ../specs/master/spec.md"
  })
  void rootTestNamingChangedFileRunsWhenThatFileChanges(
      String changed, String named, @TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(
                ROOT_TESTS + "StarterTest.java",
                "class StarterTest { String f = \"" + named + "\"; }")
            .file(changed, "a: 1")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of(changed));

    assertThat(plan.lines())
        .contains(GRADLE + "900 ./gradlew :test --tests '*StarterTest' -x pitestDiff");
  }

  @Test
  void roleFileRunsTheFitnessTestHoldingTheRoleNameRules(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(ROOT_TESTS + "FitnessTest.java", "class FitnessTest {}")
            .file(
                "orchestrator-backend/src/test/java/technical/m/RoleNamesFitness.java",
                "class RoleNamesFitness {}")
            .file("roles/a/role.yml", "id: a")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of("roles/a/role.yml"));

    assertThat(plan.lines())
        .containsExactly(GRADLE + "900 ./gradlew :test --tests '*FitnessTest' -x pitestDiff");
  }

  @Test
  void roleBriefChangeRunsThatRolesEvalCases(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("roles/a/brief.md.dist", "## ship")
            .file("roles/a/eval/01-a-ship.yml", "case: a ship")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of("roles/a/brief.md.dist"));

    assertThat(plan.lines()).contains(GRADLE + "1800 ./gradlew roleEval -Prole=a");
  }

  @Test
  void buildLogicChangeRunsTheBuildLogicTests(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("orchestrator-backend/build-logic/src/main/java/a/B.java", "class B {}")
            .tracked();

    TestsFor.Plan plan =
        testsFor.plan(List.of("orchestrator-backend/build-logic/src/main/java/a/B.java"));

    assertThat(plan.lines())
        .containsExactly("cd orchestrator-backend/build-logic && timeout 600 ../gradlew test");
  }

  @Test
  void boardChangeRunsTheBoardScriptsAndAssets(@TempDir Path repo) throws Exception {
    TestsFor testsFor = TestRepoBuilder.in(repo).file("board/app.js", "x").tracked();

    TestsFor.Plan plan = testsFor.plan(List.of("board/app.js"));

    assertThat(plan.lines()).contains("cd board && timeout 600 sh -c 'npm run lint && npm test'");
  }

  @Test
  void boardChangeNamesTheBoardTestLine(@TempDir Path repo) throws Exception {
    TestsFor testsFor = TestRepoBuilder.in(repo).file("board/app.css", "x").tracked();

    TestsFor.Plan plan = testsFor.plan(List.of("board/app.css"));

    assertThat(plan.lines()).contains(GRADLE + "900 ./gradlew boardTest");
  }

  @Test
  void failsNamingTheCommandWhenTheBaseBranchIsMissing(@TempDir Path repo) throws Exception {
    TestsFor testsFor = TestRepoBuilder.in(repo).file("board/app.js", "x").tracked();

    assertThatThrownBy(() -> testsFor.changedAgainst("release"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("git failed, exit")
        .hasMessageContaining("release...HEAD");
  }

  @Test
  void locatesRootSourcesWhenTheTaskDirectoryIsTheRepositoryTop(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("src/main/java/a/Foo.java", "class Foo {}")
            .file("src/test/java/a/FooTest.java", "class FooTest {}")
            .trackedAtTheTop();

    TestsFor.Plan plan = testsFor.plan(List.of("src/main/java/a/Foo.java"));

    assertThat(plan.lines())
        .contains("cd . && timeout 900 ./gradlew :test --tests '*FooTest' -x pitestDiff");
  }

  @Test
  void pathsGivenWithTheRepositoryRootAreStrippedToRelativeOnes(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor = TestRepoBuilder.in(repo).file("board/app.js", "x").tracked();

    String stripped = testsFor.stripRoot(repo.toRealPath() + "/board/app.js");

    assertThat(stripped).isEqualTo("board/app.js");
  }

  @Test
  void failsNamingThePathWhenAnEntryNamesNoFileAndNoDeletion(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("orchestrator-backend/src/main/java/a/Foo.java", "x")
            .committed();

    assertThatThrownBy(
            () ->
                testsFor.resolve(
                    List.of("../orchestrator-backend/src/main/java/a/Gone.java"), "HEAD"))
        .isInstanceOf(UnknownPathException.class)
        .hasMessageContaining("orchestrator-backend/src/main/java/a/Gone.java");
  }

  @Test
  void resolvesDotDotEntriesAgainstTheBackendWhenTheFileExists(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file("orchestrator-backend/src/main/java/a/Foo.java", "x")
            .committed();

    List<String> resolved =
        testsFor.resolve(List.of("../orchestrator-backend/src/main/java/a/Foo.java"), "HEAD");

    assertThat(resolved).containsExactly("orchestrator-backend/src/main/java/a/Foo.java");
  }

  @Test
  void readsThePointersTargetWhenChangedPathMatchesItsGlob(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(
                ".claude/rules/board.md",
                "---\npaths:\n  - \"board/**\"\n---\n\n"
                    + "Read [`b`](../../openspec/specs/board/spec.md).\n")
            .file("board/app.js", "x")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of("board/app.js"));

    assertThat(plan.reads()).containsExactly("openspec/specs/board/spec.md");
  }

  @Test
  void readsNothingWhenNoPointerGlobMatchesTheChangedPath(@TempDir Path repo) throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(
                ".claude/rules/board.md",
                "---\npaths:\n  - \"board/**\"\n---\n\n"
                    + "Read [`b`](../../openspec/specs/board/spec.md).\n")
            .file("docs/x.md", "x")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of("docs/x.md"));

    assertThat(plan.reads()).isEmpty();
  }

  @Test
  void readsTheTargetOnceWhenTwoChangedPathsMatchOnePointer(@TempDir Path repo)
      throws Exception {
    TestsFor testsFor =
        TestRepoBuilder.in(repo)
            .file(
                ".claude/rules/board.md",
                "---\npaths:\n  - \"board/**\"\n---\n\n"
                    + "Read [`b`](../../openspec/specs/board/spec.md).\n")
            .file("board/a.js", "x")
            .file("board/b.js", "x")
            .tracked();

    TestsFor.Plan plan = testsFor.plan(List.of("board/a.js", "board/b.js"));

    assertThat(plan.reads()).containsExactly("openspec/specs/board/spec.md");
  }
}

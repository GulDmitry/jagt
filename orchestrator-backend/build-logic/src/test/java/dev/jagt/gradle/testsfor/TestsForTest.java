package dev.jagt.gradle.testsfor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TestsForTest {

  private static final String ROOT_TESTS =
      "orchestrator-backend/src/test/java/dev/jagt/orchestrator/";
  private static final String ADAPTER = "orchestrator-backend/adapter/src/";
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
}

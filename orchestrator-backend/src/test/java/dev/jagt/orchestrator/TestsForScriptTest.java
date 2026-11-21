package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TestsForScriptTest {

  private static final String ROOT_TESTS =
      "orchestrator-backend/src/test/java/dev/jagt/orchestrator/";
  private static final String ADAPTER = "orchestrator-backend/adapter/src/";

  @Test
  void printsCompileCheckstyleTouchedTestsThenRuleTestsInOrderWhenMainClassChanges(
      @TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve(ADAPTER + "main/java/a"));
    Files.createDirectories(repo.resolve(ADAPTER + "test/java/a"));
    Files.createDirectories(repo.resolve(ROOT_TESTS));
    Files.writeString(repo.resolve(ADAPTER + "main/java/a/Foo.java"), "class Foo {}");
    Files.writeString(repo.resolve(ADAPTER + "test/java/a/FooTest.java"), "class FooTest {}");
    Files.writeString(repo.resolve(ROOT_TESTS + "PackagesTest.java"), "class PackagesTest {}");

    Process run =
        TestTestsForProcessBuilder.over(repo).build(ADAPTER + "main/java/a/Foo.java").start();
    List<String> lines =
        new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(lines)
        .filteredOn(l -> l.startsWith("cd "))
        .satisfiesExactly(
            l -> assertThat(l).contains(":adapter:compileJava :adapter:compileTestJava"),
            l -> assertThat(l).contains(":adapter:checkstyleMain :adapter:checkstyleTest"),
            l -> assertThat(l).contains(":adapter:test --tests '*FooTest' -x pitestDiff"),
            l -> assertThat(l).contains(":test --tests '*PackagesTest' -x pitestDiff"));
  }

  @Test
  void selectsTestOfDependentModuleThatNamesTheChangedClass(@TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve("orchestrator-backend/core/src/main/java/c"));
    Files.createDirectories(repo.resolve("orchestrator-backend/usecase/src/test/java/u"));
    Files.writeString(
        repo.resolve("orchestrator-backend/core/src/main/java/c/Ticket.java"), "class Ticket {}");
    Files.writeString(
        repo.resolve("orchestrator-backend/usecase/src/test/java/u/BoardTest.java"),
        "class BoardTest { Ticket t; }");

    Process run =
        TestTestsForProcessBuilder.over(repo)
            .build("orchestrator-backend/core/src/main/java/c/Ticket.java")
            .start();
    String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(output).contains(":usecase:test --tests '*BoardTest' -x pitestDiff");
  }

  @Test
  void compilesEverySourceSetWhenMainClassIsRemoved(@TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve(ROOT_TESTS));

    Process run =
        TestTestsForProcessBuilder.over(repo)
            .build("orchestrator-backend/surface/src/main/java/s/Gone.java")
            .start();
    String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(output)
        .contains(":surface:compileJava")
        .contains(":compileE2eJava")
        .contains(":stand:compileStandJava");
  }

  @Test
  void runsOnlyTheDocumentRuleTestsThatExistWhenMarkdownChanges(@TempDir Path repo)
      throws Exception {
    Files.createDirectories(repo.resolve(ROOT_TESTS));
    Files.createDirectories(repo.resolve("docs"));
    Files.writeString(repo.resolve(ROOT_TESTS + "FitnessTest.java"), "class FitnessTest {}");
    Files.writeString(repo.resolve("docs/a.md"), "# a");

    Process run = TestTestsForProcessBuilder.over(repo).build("docs/a.md").start();
    List<String> lines =
        new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(lines)
        .anyMatch(l -> l.contains("no rule test DocLinksTest, dropped"))
        .anyMatch(l -> l.endsWith(":test --tests '*FitnessTest' -x pitestDiff"))
        .noneMatch(l -> l.contains("compileJava"));
  }

  @Test
  void ruleTestAlsoNamingTheChangedWorkflowRunsOnceWhenWorkflowChanges(@TempDir Path repo)
      throws Exception {
    Files.createDirectories(repo.resolve(ROOT_TESTS));
    Files.createDirectories(repo.resolve(".github/workflows"));
    Files.writeString(
        repo.resolve(ROOT_TESTS + "CiWorkflowTest.java"),
        "class CiWorkflowTest { String f = \"ci.yml\"; }");
    Files.writeString(repo.resolve(".github/workflows/ci.yml"), "on: push");

    Process run = TestTestsForProcessBuilder.over(repo).build(".github/workflows/ci.yml").start();
    String out = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(out).containsOnlyOnce("'*CiWorkflowTest'");
  }

  @ParameterizedTest
  @CsvSource({
    "app.server.yml.dist, app.server.yml.dist",
    "specs/master/spec.md, ../specs/master/spec.md"
  })
  void rootTestNamingChangedFileRunsWhenThatFileChanges(
      String changed, String named, @TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve(ROOT_TESTS));
    Files.writeString(
        repo.resolve(ROOT_TESTS + "StarterTest.java"),
        "class StarterTest { String f = \"" + named + "\"; }");
    Files.createDirectories(repo.resolve(changed).getParent());
    Files.writeString(repo.resolve(changed), "a: 1");

    Process run = TestTestsForProcessBuilder.over(repo).build(changed).start();
    String out = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(out).contains("--tests '*StarterTest'");
  }

  @Test
  void roleFileRunsTheFitnessTestHoldingTheRoleNameRules(@TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve(ROOT_TESTS));
    Files.createDirectories(repo.resolve("orchestrator-backend/src/test/java/technical/m"));
    Files.createDirectories(repo.resolve("roles/a"));
    Files.writeString(repo.resolve(ROOT_TESTS + "FitnessTest.java"), "class FitnessTest {}");
    Files.writeString(
        repo.resolve("orchestrator-backend/src/test/java/technical/m/RoleNamesFitness.java"),
        "class RoleNamesFitness {}");
    Files.writeString(repo.resolve("roles/a/role.yml"), "id: a");

    Process run = TestTestsForProcessBuilder.over(repo).build("roles/a/role.yml").start();
    String out = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(out).containsOnlyOnce(":test --tests '*FitnessTest' -x pitestDiff");
  }

  @Test
  void newRuleDocumentRunsTheFitnessTestCountingRuleDocuments(@TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve(ROOT_TESTS));
    Files.createDirectories(repo.resolve("docs/rules"));
    Files.writeString(repo.resolve(ROOT_TESTS + "FitnessTest.java"), "class FitnessTest {}");
    Files.writeString(repo.resolve("docs/rules/new.md"), "# new");

    Process run = TestTestsForProcessBuilder.over(repo).build("docs/rules/new.md").start();
    String out = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(out).contains(":test --tests '*FitnessTest' -x pitestDiff");
  }
}

package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestsForScriptTest {

  private static final String ROOT_TESTS =
      "orchestrator-backend/src/test/java/dev/jagt/orchestrator/";
  private static final String ADAPTER = "orchestrator-backend/adapter/src/";

  @Test
  void printsCompileCheckstyleTouchedTestsThenRuleTestsInOrderWhenAMainClassChanges(
      @TempDir Path repo) throws Exception {
    Files.createDirectories(repo.resolve(ADAPTER + "main/java/a"));
    Files.createDirectories(repo.resolve(ADAPTER + "test/java/a"));
    Files.createDirectories(repo.resolve(ROOT_TESTS));
    Files.writeString(repo.resolve(ADAPTER + "main/java/a/Foo.java"), "class Foo {}");
    Files.writeString(repo.resolve(ADAPTER + "test/java/a/FooTest.java"), "class FooTest {}");
    Files.writeString(repo.resolve(ROOT_TESTS + "DuplicationTest.java"), "class DuplicationTest {}");

    Process run =
        TestTestsForProcessBuilder.over(repo).build(ADAPTER + "main/java/a/Foo.java").start();
    List<String> lines =
        new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(lines).hasSize(4);
    assertThat(lines.get(0))
        .contains("timeout 600 ./gradlew :adapter:compileJava :adapter:compileTestJava");
    assertThat(lines.get(1)).contains(":adapter:checkstyleMain :adapter:checkstyleTest");
    assertThat(lines.get(2)).contains(":adapter:test --tests '*FooTest' -x pitestDiff");
    assertThat(lines.get(3)).contains(":test --tests '*DuplicationTest' -x pitestDiff");
  }

  @Test
  void selectsATestOfADependentModuleThatNamesTheChangedClass(@TempDir Path repo)
      throws Exception {
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
  void compilesEverySourceSetWhenAMainClassIsRemoved(@TempDir Path repo) throws Exception {
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
    Files.writeString(repo.resolve(ROOT_TESTS + "TextBudgetTest.java"), "class TextBudgetTest {}");
    Files.writeString(repo.resolve("docs/a.md"), "# a");

    Process run = TestTestsForProcessBuilder.over(repo).build("docs/a.md").start();
    List<String> lines =
        new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();

    assertThat(run.waitFor(60, TimeUnit.SECONDS)).isTrue();
    assertThat(lines)
        .anyMatch(l -> l.contains("no rule test DocLinksTest, dropped"))
        .anyMatch(l -> l.endsWith(":test --tests '*TextBudgetTest' -x pitestDiff"))
        .noneMatch(l -> l.contains("compileJava"));
  }
}

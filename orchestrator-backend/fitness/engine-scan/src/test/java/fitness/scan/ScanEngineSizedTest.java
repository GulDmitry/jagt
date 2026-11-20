package fitness.scan;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Measurement;
import fitness.core.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanEngineSizedTest {

  private final ScanEngine engine = new ScanEngine();

  @Test
  void reportsTheLongestFileAndListsOnlyTheFilesOverTheLimit(@TempDir Path root)
      throws IOException {
    Files.writeString(root.resolve("a.md"), "1\n2\n3\n");
    Files.writeString(root.resolve("b.md"), "1\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "*.md")
                .select("per", "file")
                .select("measure", "lines")
                .limit(2)
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.value()).isEqualTo(3);
    assertThat(found.items()).containsExactly("3 a.md");
  }

  @Test
  void measuresEverySectionBetweenTheMarkerLines(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("a.md"), "intro\n## step-one\na\nb\n## step-two\nc\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "a.md")
                .select("per", "section")
                .select("measure", "lines")
                .select("regex", "(?m)^## [a-z-]+$")
                .limit(2)
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("3 a.md ## step-one");
  }

  @Test
  void measuresTheLinesTheRegexKeepsAndLeavesTheRest(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("a.md"), "ab\n| a table row far too long\nabcdef\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "a.md")
                .select("per", "line")
                .select("measure", "characters")
                .select("regex", "^[^|]")
                .limit(4)
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.value()).isEqualTo(6);
    assertThat(found.items()).containsExactly("6 a.md: abcdef");
  }

  @Test
  void countsTheWordsOfEverySentenceOfTheProse(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("a.md"), "One two three four five six. Short one here.\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "a.md")
                .select("per", "sentence")
                .select("measure", "words")
                .limit(5)
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("6 a.md: One two three four five six.");
  }

  @Test
  void reportsTheShareOfCommentLinesInPercent(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("A.java"), "// c\ncode;\n\n/* x */\ncode2;\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "A.java")
                .select("per", "project")
                .select("measure", "comment-percent")
                .limit(15)
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.value()).isEqualTo(50);
    assertThat(found.items()).containsExactly("50 2 comment lines in 4");
  }

  @Test
  void reportsAMethodPastTheCognitiveComplexityLimit(@TempDir Path root) throws IOException {
    Files.writeString(
        root.resolve("A.java"),
        "class A { void m(boolean a, boolean b) { if (a) { if (b) { return; } } } }\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "A.java")
                .select("per", "method")
                .select("measure", "cognitive-complexity")
                .limit(2)
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.value()).isEqualTo(3);
    assertThat(found.items()).singleElement().asString().startsWith("3 A.java");
  }

  @Test
  void reportsABlockCopiedIntoTwoFilesAtOrAboveTheTokenLimit(@TempDir Path root)
      throws IOException {
    String body =
        "int f() { int a = 1; int b = 2; int c = a + b; int d = c * a; return d + b + c; }";
    Files.writeString(root.resolve("A.java"), "class A { " + body + " }\n");
    Files.writeString(root.resolve("B.java"), "class B { " + body + " }\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "*.java")
                .select("per", "duplicate")
                .select("measure", "tokens")
                .limit(20)
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.value()).isGreaterThan(20);
    assertThat(found.items()).singleElement().asString().contains("A.java", "B.java");
  }

  @Test
  void countsTheMockedTypesOfEachTestIncludingTheSharedOnes(@TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("src/test/java"));
    Files.writeString(
        root.resolve("src/test/java/T.java"),
        "class T { @Mock A a; @Mock B b; @Test void x() { mock(C.class); } }\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("set", "test")
                .select("per", "test")
                .select("measure", "mocked-types")
                .limit(2)
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("3 T.java [A, B, C]");
  }

  @Test
  void countsClassesPerPackageBelowTheBasePackageWithoutPackageInfo(@TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("src/main/java/a/b"));
    Files.writeString(root.resolve("src/main/java/a/Y.java"), "class Y {}\n");
    Files.writeString(root.resolve("src/main/java/a/b/X1.java"), "class X1 {}\n");
    Files.writeString(root.resolve("src/main/java/a/b/X2.java"), "class X2 {}\n");
    Files.writeString(root.resolve("src/main/java/a/b/package-info.java"), "package a.b;\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("set", "main")
                .select("per", "package")
                .select("measure", "classes")
                .limit(1)
                .build(),
            new Project(root, "a", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("2 b");
  }

  @Test
  void readsOnlySourceRootsWithinAModuleOfTheRoot(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("mod/src/main/java"));
    Files.createDirectories(root.resolve("mod/inner/src/main/java"));
    Files.writeString(root.resolve("mod/src/main/java/A.java"), "class A {}\n");
    Files.writeString(root.resolve("mod/inner/src/main/java/B.java"), "class B {}\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("set", "main").build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("mod/src/main/java/A.java");
  }

  @Test
  void unitesTheFilesOfEveryGlobOfAList(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("a.md"), "");
    Files.writeString(root.resolve("b.txt"), "");
    Files.writeString(root.resolve("c.md"), "");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("glob", "*.txt,c.md").select("per", "name").build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("b.txt", "c.md");
  }

  @Test
  void findsALiteralPathOutsideTheRootWithoutWalkingItsParent(@TempDir Path outer)
      throws IOException {
    Files.createDirectories(outer.resolve("inner"));
    Files.writeString(outer.resolve("AGENTS.md"), "");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("glob", "../AGENTS.md").build(),
            new Project(outer.resolve("inner"), "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("../AGENTS.md");
  }
}

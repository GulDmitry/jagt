package fitness.scan;

import static org.assertj.core.api.Assertions.assertThat;

import fitness.core.Measurement;
import fitness.core.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanEngineTest {

  private final ScanEngine engine = new ScanEngine();

  @Test
  void listsFileNamesMatchingAGlobSorted(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("docs"));
    Files.writeString(root.resolve("docs/b.md"), "");
    Files.writeString(root.resolve("docs/a.md"), "");
    Files.writeString(root.resolve("docs/c.txt"), "");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("glob", "docs/*.md").select("per", "name").build(),
            new Project(root, "", List.of("all")));

    assertThat(found.items()).containsExactly("a.md", "b.md");
  }

  @Test
  void countsDirectoriesWhenTheKindIsDirectory(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("roles/one"));
    Files.createDirectories(root.resolve("roles/two"));
    Files.writeString(root.resolve("roles/readme.md"), "");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "roles/*")
                .select("kind", "directory")
                .select("per", "name")
                .build(),
            new Project(root, "", List.of("all")));

    assertThat(found.items()).containsExactly("one", "two");
  }

  @Test
  void aDoubleStarMatchesTheDirectoryItselfAndEverythingBelow(@TempDir Path root)
      throws IOException {
    Files.createDirectories(root.resolve("logic/nested"));
    Files.writeString(root.resolve("logic/top.gradle"), "");
    Files.writeString(root.resolve("logic/nested/deep.groovy"), "");
    Files.writeString(root.resolve("logic/notes.md"), "");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("glob", "logic/**/*.{gradle,groovy}").build(),
            new Project(root, "", List.of("all")));

    assertThat(found.items()).containsExactly("logic/nested/deep.groovy", "logic/top.gradle");
  }

  @Test
  void skipsBuildOutput(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("build"));
    Files.writeString(root.resolve("build/out.md"), "");
    Files.writeString(root.resolve("real.md"), "");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("glob", "**/*.md").build(),
            new Project(root, "", List.of("all")));

    assertThat(found.items()).containsExactly("real.md");
  }

  @Test
  void aGlobMayLeaveTheRootThroughItsParent(@TempDir Path outer) throws IOException {
    Files.createDirectories(outer.resolve("inner"));
    Files.createDirectories(outer.resolve("docs"));
    Files.writeString(outer.resolve("docs/x.md"), "");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder().select("glob", "../docs/*.md").select("per", "name").build(),
            new Project(outer.resolve("inner"), "", List.of("all")));

    assertThat(found.items()).containsExactly("x.md");
  }

  @Test
  void listsTheTestFilesWhoseTextMatchesTheRegex(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("src/test/java"));
    Files.createDirectories(root.resolve("src/main/java"));
    Files.createDirectories(root.resolve("mod/src/e2e/java"));
    Files.writeString(root.resolve("src/test/java/Boots.java"), "import a.Boot;\n");
    Files.writeString(root.resolve("src/test/java/Plain.java"), "class Plain {}\n");
    Files.writeString(root.resolve("src/main/java/Main.java"), "import a.Boot;\n");
    Files.writeString(root.resolve("mod/src/e2e/java/Flow.java"), "import a.Boot;\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("set", "test")
                .select("regex", "(?m)^import a\\.Boot;")
                .build(),
            new Project(root, "", List.of("all")));

    assertThat(found.items())
        .containsExactly("mod/src/e2e/java/Flow.java", "src/test/java/Boots.java");
  }

  @Test
  void listsTheFirstGroupOfEveryMatch(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("settings.gradle"), "include 'a'\ninclude 'b'\nname 'c'\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "settings.gradle")
                .select("regex", "(?m)^include\\s+'([^']+)'")
                .select("per", "match")
                .build(),
            new Project(root, "", List.of("all")));

    assertThat(found.items()).containsExactly("a", "b");
  }

  @Test
  void listsEveryMatchingLineWithItsFile(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("build.gradle"), "  api 'x'\n  other\n");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "build.gradle")
                .select("regex", "api")
                .select("per", "line")
                .build(),
            new Project(root, "", List.of("all")));

    assertThat(found.items()).containsExactly("build.gradle api 'x'");
  }
}

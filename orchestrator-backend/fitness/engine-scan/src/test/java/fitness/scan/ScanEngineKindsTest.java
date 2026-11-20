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

class ScanEngineKindsTest {

  private final ScanEngine engine = new ScanEngine();

  @Test
  void listsTheDirectoriesLackingTheRequiredFileHoldingTheRegex(@TempDir Path root)
      throws IOException {
    Path marked = Files.createDirectories(root.resolve("src/main/java/a"));
    Path unmarked = Files.createDirectories(root.resolve("src/main/java/b"));
    Path missing = Files.createDirectories(root.resolve("src/main/java/c"));
    Files.writeString(marked.resolve("A.java"), "class A {}");
    Files.writeString(marked.resolve("package-info.java"), "@NullMarked package a;");
    Files.writeString(unmarked.resolve("B.java"), "class B {}");
    Files.writeString(unmarked.resolve("package-info.java"), "package b;");
    Files.writeString(missing.resolve("C.java"), "class C {}");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("set", "main")
                .select("requires", "package-info.java")
                .select("regex", "@NullMarked")
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("src/main/java/b", "src/main/java/c");
  }

  @Test
  void listsTheTestMethodsThatReachNoAssertion(@TempDir Path root) throws IOException {
    Path tests = Files.createDirectories(root.resolve("src/test/java"));
    Files.writeString(
        tests.resolve("SampleTest.java"),
        """
        class SampleTest {
          @Test
          void asserts() {
            assertThat(1).isEqualTo(1);
          }

          @Test
          void forgets() {
            call();
          }
        }
        """);

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("set", "test")
                .select("per", "test")
                .select("measure", "assertionless")
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("SampleTest.forgets");
  }

  @Test
  void readsEveryTrackedTextFileAndNoUntrackedOne(@TempDir Path root)
      throws IOException, InterruptedException {
    Files.writeString(root.resolve("kept.txt"), "<<<<<<< ours\n");
    Files.writeString(root.resolve("stray.txt"), "<<<<<<< ours\n");
    Files.write(root.resolve("binary.bin"), new byte[] {'<', '<', '<', 0, 1});
    new ProcessBuilder("git", "-C", root.toString(), "init", "-q").start().waitFor();
    new ProcessBuilder("git", "-C", root.toString(), "add", "kept.txt", "binary.bin")
        .start()
        .waitFor();

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("set", "tracked")
                .select("per", "file")
                .select("regex", "(?m)^<{7} ")
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("kept.txt");
  }
}

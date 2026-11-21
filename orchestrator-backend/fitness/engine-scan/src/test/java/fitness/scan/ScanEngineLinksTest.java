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

class ScanEngineLinksTest {

  private final ScanEngine engine = new ScanEngine();

  @Test
  void listsTheLinksWhoseTargetDoesNotExist(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("exists.md"), "text");
    Files.writeString(
        root.resolve("page.md"),
        "[a](exists.md) [b](gone.md) [c](https://example.org/x) [d](exists.md#part) [e](#top)");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "page.md")
                .select("per", "link")
                .select("measure", "unresolved")
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("page.md -> gone.md");
  }

  @Test
  void listsTheLinkedFileThatLinksOnToAnotherFileOfItsDirectory(@TempDir Path root)
      throws IOException {
    Path skill = Files.createDirectories(root.resolve("skill"));
    Files.writeString(
        skill.resolve("SKILL.md"), "[deep](deep.md) [flat](flat.md) [out](../outside.md)");
    Files.writeString(skill.resolve("deep.md"), "see [flat](flat.md)");
    Files.writeString(skill.resolve("flat.md"), "see [out](../outside.md)");
    Files.writeString(root.resolve("outside.md"), "see [x](skill/flat.md)");

    Measurement found =
        engine.measure(
            new TestFitnessFunctionBuilder()
                .select("glob", "skill/SKILL.md")
                .select("per", "link")
                .select("measure", "nested")
                .build(),
            new Project(root, "", List.of("all"), Map.of()));

    assertThat(found.items()).containsExactly("skill/SKILL.md -> deep.md");
  }
}

package fitness.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

class FitnessTestsTest {

  @AfterEach
  void clearOnly() {
    System.clearProperty("fitness.only");
  }

  @Test
  void nestsDimensionCharacteristicAndSubcharacteristicAroundTheFitnessFunction(@TempDir Path directory)
      throws IOException {
    Files.writeString(
        directory.resolve("fitness.yml"),
        """
        schema: 1
        technical:
          maintainability:
            modularity:
              docs: { engine: scan, glob: "*.md", governs: count, max: 0 }
        """);

    List<DynamicNode> dimensions = FitnessTests.from(directory.resolve("fitness.yml")).toList();

    DynamicContainer technical = (DynamicContainer) dimensions.getFirst();
    DynamicContainer maintainability = (DynamicContainer) technical.getChildren().toList().getFirst();
    DynamicContainer modularity = (DynamicContainer) maintainability.getChildren().toList().getFirst();
    assertThat(dimensions).extracting(DynamicNode::getDisplayName)
        .containsExactly("technical", "data", "security", "operational");
    assertThat(maintainability.getDisplayName()).isEqualTo("maintainability");
    assertThat(modularity.getChildren().toList()).extracting(DynamicNode::getDisplayName).containsExactly("docs");
  }

  @Test
  void aFitnessFunctionOverItsNumberFailsNamingItsItems(@TempDir Path directory) throws IOException {
    Files.writeString(directory.resolve("a.md"), "");
    Files.writeString(
        directory.resolve("fitness.yml"),
        """
        schema: 1
        technical:
          maintainability:
            modularity:
              docs: { engine: scan, glob: "*.md", per: name, governs: size, max: 0 }
        """);

    DynamicTest test =
        (DynamicTest)
            ((DynamicContainer)
                    ((DynamicContainer)
                            ((DynamicContainer)
                                    FitnessTests.from(directory.resolve("fitness.yml"))
                                        .toList()
                                        .getFirst())
                                .getChildren()
                                .toList()
                                .getFirst())
                        .getChildren()
                        .toList()
                        .getFirst())
                .getChildren()
                .toList()
                .getFirst();

    assertThatThrownBy(test.getExecutable()::execute)
        .isInstanceOf(AssertionFailedError.class)
        .hasMessageContaining("docs")
        .hasMessageContaining("a.md");
  }

  @Test
  void aFitnessFunctionWithinItsNumberPasses(@TempDir Path directory) throws IOException {
    Files.writeString(directory.resolve("a.md"), "");
    Files.writeString(
        directory.resolve("fitness.yml"),
        """
        schema: 1
        technical:
          maintainability:
            modularity:
              docs: { engine: scan, glob: "*.md", governs: count, max: 1 }
        """);

    DynamicTest test =
        (DynamicTest)
            ((DynamicContainer)
                    ((DynamicContainer)
                            ((DynamicContainer)
                                    FitnessTests.from(directory.resolve("fitness.yml"))
                                        .toList()
                                        .getFirst())
                                .getChildren()
                                .toList()
                                .getFirst())
                        .getChildren()
                        .toList()
                        .getFirst())
                .getChildren()
                .toList()
                .getFirst();

    assertThatCode(test.getExecutable()::execute).doesNotThrowAnyException();
  }

  @Test
  void aCountedKindBelowItsNumberFailsAskingToLowerIt(@TempDir Path directory) throws IOException {
    Files.writeString(
        directory.resolve("fitness.yml"),
        """
        schema: 1
        technical:
          maintainability:
            modularity:
              docs: { engine: scan, glob: "*.md", governs: count, max: 2 }
        """);

    DynamicTest test =
        (DynamicTest)
            ((DynamicContainer)
                    ((DynamicContainer)
                            ((DynamicContainer)
                                    FitnessTests.from(directory.resolve("fitness.yml"))
                                        .toList()
                                        .getFirst())
                                .getChildren()
                                .toList()
                                .getFirst())
                        .getChildren()
                        .toList()
                        .getFirst())
                .getChildren()
                .toList()
                .getFirst();

    assertThatThrownBy(test.getExecutable()::execute).hasMessageContaining("lower max to 0");
  }

  @Test
  void aRowMeasuredByAToolHasNoTestHere(@TempDir Path directory) throws IOException {
    Files.writeString(
        directory.resolve("fitness.yml"),
        """
        schema: 1
        operational:
          performance-efficiency:
            resource-utilisation:
              contexts: { engine: tool, tool: gradle, governs: count, max: 2 }
        """);

    List<DynamicNode> dimensions = FitnessTests.from(directory.resolve("fitness.yml")).toList();

    DynamicContainer operational = (DynamicContainer) dimensions.get(3);
    DynamicContainer performance = (DynamicContainer) operational.getChildren().toList().getFirst();
    DynamicContainer resources = (DynamicContainer) performance.getChildren().toList().get(1);
    assertThat(resources.getChildren()).isEmpty();
  }

  @Test
  void onlyNamesOneFitnessFunctionAndRefusesAnUnknownOne(@TempDir Path directory) throws IOException {
    Files.writeString(
        directory.resolve("fitness.yml"),
        """
        schema: 1
        technical:
          maintainability:
            modularity:
              docs: { engine: scan, glob: "*.md", governs: count, max: 0 }
        """);
    System.setProperty("fitness.only", "nobody");

    assertThatThrownBy(() -> FitnessTests.from(directory.resolve("fitness.yml")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nobody");
  }
}

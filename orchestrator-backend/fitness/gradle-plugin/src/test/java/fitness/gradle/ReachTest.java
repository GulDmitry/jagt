package fitness.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReachTest {

  @TempDir Path directory;

  @Test
  void namesAClassWithABranchAndNoCoveredLineTogetherWithItsModule() throws IOException {
    Path classes = Files.createDirectories(directory.resolve("classes/a"));
    Files.createFile(classes.resolve("Gate.class"));
    Path xml = directory.resolve("reach.xml");
    Files.writeString(
        xml,
        """
        <report name="r"><package name="a">
          <class name="a/Gate"><counter type="BRANCH" missed="2" covered="0"/><counter type="LINE" missed="4" covered="0"/></class>
          <class name="a/Used"><counter type="BRANCH" missed="1" covered="1"/><counter type="LINE" missed="0" covered="3"/></class>
          <class name="a/Plain"><counter type="LINE" missed="2" covered="0"/></class>
        </package></report>
        """);

    assertThat(Reach.unreached(xml, Map.of(":m", directory.resolve("classes"))))
        .containsExactly("a.Gate (:m)");
  }

  @Test
  void readsTheLineCoveragePercentOfTheWholeReport() throws IOException {
    Path xml = directory.resolve("reach.xml");
    Files.writeString(
        xml,
        """
        <report name="r"><package name="a">
          <class name="a/A"><counter type="LINE" missed="1" covered="1"/></class>
          <counter type="LINE" missed="1" covered="1"/></package>
          <counter type="BRANCH" missed="9" covered="0"/>
          <counter type="LINE" missed="25" covered="75"/></report>
        """);

    assertThat(Reach.linePercent(xml)).isEqualTo(75);
  }

  @Test
  void countsANestedClassAgainstItsOuterClass() throws IOException {
    Path xml = directory.resolve("reach.xml");
    Files.writeString(
        xml,
        """
        <report name="r"><package name="a">
          <class name="a/Outer"><counter type="LINE" missed="0" covered="3"/></class>
          <class name="a/Outer$Inner"><counter type="BRANCH" missed="2" covered="0"/><counter type="LINE" missed="4" covered="0"/></class>
        </package></report>
        """);

    assertThat(Reach.unreached(xml, Map.of())).isEmpty();
  }
}

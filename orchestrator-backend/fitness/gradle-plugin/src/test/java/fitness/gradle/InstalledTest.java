package fitness.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstalledTest {

  @ParameterizedTest(name = "{0} is missing when its Gradle plugin {1} is not applied")
  @CsvSource({"checkstyle,checkstyle", "jacoco,jacoco", "pit,info.solidsoft.pitest"})
  void aToolRunByAGradlePluginIsThereOnlyWhenThePluginIsApplied(String tool, String plugin) {
    assertThat(new Installed(Set.of(), "").has(tool)).isFalse();
    assertThat(new Installed(Set.of(plugin), "").has(tool)).isTrue();
  }

  @Test
  void anExecutableIsThereOnlyWhenItSitsOnThePath(@TempDir Path bin) throws IOException {
    Path scanner = Files.createFile(bin.resolve("osv-scanner"));

    assertThat(new Installed(Set.of(), bin.toString()).has("osv-scanner")).isFalse();
    assertThat(scanner.toFile().setExecutable(true)).isTrue();
    assertThat(new Installed(Set.of(), bin.toString()).has("osv-scanner")).isTrue();
  }

  @Test
  void aNeedItKnowsNothingOfIsLeftToTheRowsOwnMeasure() {
    assertThat(new Installed(Set.of(), "").has("anything")).isTrue();
  }
}

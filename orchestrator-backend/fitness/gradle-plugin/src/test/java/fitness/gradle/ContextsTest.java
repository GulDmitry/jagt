package fitness.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextsTest {

  @TempDir Path reports;

  @Test
  void sumsTheHighestMissCountOfEachCacheAcrossReports() throws IOException {
    Files.writeString(reports.resolve("a.xml"), "DefaultContextCache@1f size = 1, missCount = 1\nDefaultContextCache@1f size = 2, missCount = 2");
    Files.writeString(reports.resolve("b.xml"), "DefaultContextCache@2c size = 1, missCount = 3");

    assertThat(Contexts.created(reports)).isEqualTo(5);
  }

  @Test
  void countsNoContextWhenTheSuiteWroteNoReport() {
    assertThat(Contexts.created(reports.resolve("absent"))).isZero();
  }
}

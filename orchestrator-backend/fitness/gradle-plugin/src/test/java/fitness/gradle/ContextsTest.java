package fitness.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextsTest {

  private static final Pattern MISSES = Pattern.compile("Cache@(\\w+) .*?missCount = (\\d+)");

  @TempDir Path reports;

  @Test
  void sumsTheHighestMissCountOfEachCacheAcrossReports() throws IOException {
    Files.writeString(reports.resolve("a.xml"), "Cache@1f size = 1, missCount = 1\nCache@1f size = 2, missCount = 2");
    Files.writeString(reports.resolve("b.xml"), "Cache@2c size = 1, missCount = 3");

    assertThat(Contexts.created(reports, MISSES)).isEqualTo(5);
  }

  @Test
  void countsNoContextWhenTheSuiteWroteNoReport() {
    assertThat(Contexts.created(reports.resolve("absent"), MISSES)).isZero();
  }
}

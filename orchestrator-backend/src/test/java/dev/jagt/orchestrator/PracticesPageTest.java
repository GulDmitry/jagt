package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticesPageTest {

  @Test
  void everyRuleIsReachedFromThePracticesPage() {
    String page = DocFiles.read(Path.of("../docs/practices.md"));

    List<String> unreached =
        DocFiles.namesIn(Path.of("../docs/rules")).stream()
            .filter(rule -> !page.contains("](rules/" + rule))
            .toList();

    assertThat(unreached).isEmpty();
  }
}

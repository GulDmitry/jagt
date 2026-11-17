package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandBooksTest {

  @TempDir Path root;

  @Test
  void runThatRecordedNothingHasNoViolations() {
    StandProject.create(root);

    assertThat(new StandBooks(root).violations()).isEmpty();
  }

  @Test
  void theShippedBriefAndCasesAreRead() {
    StandBooks books = new StandBooks(root);

    assertThat(books.brief()).isNotNull();
    assertThat(books.cases()).isNotNull();
  }
}

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

    assertThat(StandBooks.at(root).violations()).isEmpty();
  }

  @Test
  void theShippedBriefAndCasesAreRead() {
    StandBooks books = StandBooks.at(root);

    assertThat(books.brief()).isNotNull();
    assertThat(books.cases()).isNotNull();
  }
}

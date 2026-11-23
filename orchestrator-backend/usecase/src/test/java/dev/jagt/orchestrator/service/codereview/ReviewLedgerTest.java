package dev.jagt.orchestrator.service.codereview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewLedgerTest {

  @TempDir Path data;

  private final OrchestratorPaths paths = mock(OrchestratorPaths.class);

  @Test
  void ledgerThatCannotBeParsedMovesAsideAndStartsEmpty() throws IOException {
    Path file = data.resolve("code-review.json");
    when(paths.codeReviewLedger()).thenReturn(file);
    Files.writeString(file, "{not json");

    List<LedgerRow> rows = new ReviewLedger(paths).rows();

    assertThat(rows).isEmpty();
    assertThat(data.resolve("code-review.json.corrupt")).exists();
    assertThat(file).doesNotExist();
  }

  @Test
  void rowSurvivesNewLedgerReadingTheSameFile() {
    when(paths.codeReviewLedger()).thenReturn(data.resolve("code-review.json"));
    new ReviewLedger(paths)
        .update("https://h/a/b/7", "shop", "", row -> row.withHeads(List.of("0123abc")));

    LedgerRow read = new ReviewLedger(paths).row("https://h/a/b/7").orElseThrow();

    assertThat(read.heads()).containsExactly("0123abc");
  }
}

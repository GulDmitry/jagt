package dev.jagt.orchestrator.job.codereview.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.host.Layout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewLedgerTest {

  @TempDir Path data;

  private final OrchestratorPaths paths = mock(OrchestratorPaths.class);

  @Test
  void ledgerThatCannotBeParsedMovesAsideAndStartsEmpty() throws IOException {
    Path file = data.resolve("jagt/code-review.json");
    Files.createDirectories(file.getParent());
    when(paths.layout()).thenReturn(new Layout(data, null));
    Files.writeString(file, "{not json");

    List<LedgerRow> rows = new ReviewLedger(paths).rows();

    assertThat(rows).isEmpty();
    assertThat(data.resolve("jagt/code-review.json.corrupt")).exists();
    assertThat(file).doesNotExist();
  }

  @Test
  void rowSurvivesNewLedgerReadingTheSameFile() {
    when(paths.layout()).thenReturn(new Layout(data, null));
    new ReviewLedger(paths)
        .update("https://h/a/b/7", "shop", "", row -> row.withHeads(List.of("0123abc")));

    LedgerRow read = new ReviewLedger(paths).row("https://h/a/b/7").orElseThrow();

    assertThat(read.heads()).containsExactly("0123abc");
  }

  @Test
  void spendAnswersAndCountsSurviveNewLedgerReadingTheSameFile() {
    when(paths.layout()).thenReturn(new Layout(data, null));
    new ReviewLedger(paths)
        .update(
            "https://h/a/b/7",
            "shop",
            "",
            row ->
                row.withCostUsd(1.25)
                    .withAnswers(Map.of("T1", 2))
                    .withCounts(Map.of("5", "1/0/fixed")));

    LedgerRow read = new ReviewLedger(paths).row("https://h/a/b/7").orElseThrow();

    assertThat(read.signals().costUsd()).isEqualTo(1.25);
    assertThat(read.answers()).containsEntry("T1", 2);
    assertThat(read.counts()).containsEntry("5", "1/0/fixed");
  }
}

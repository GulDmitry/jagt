package dev.jagt.orchestrator.job.codereview.request;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.text.AtomicFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The requests the code reviewer has met, in one file only it writes: a dedup key lives with its
 * writer and survives a restart. A file that cannot be parsed moves aside and the ledger starts
 * empty.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewLedger {

  private static final JsonMapper MAPPER = new JsonMapper();

  private final OrchestratorPaths paths;

  public synchronized List<LedgerRow> rows() {
    Path file = paths.codeReviewLedger();
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    try {
      return List.of(MAPPER.readValue(Files.readString(file), LedgerRow[].class));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    } catch (JacksonException unparsed) {
      aside(file, unparsed);
      return List.of();
    }
  }

  public synchronized Optional<LedgerRow> row(String url) {
    return rows().stream().filter(row -> row.url().equals(url)).findFirst();
  }

  /** The row for {@code url} changed by {@code change}, made where there is none. */
  public synchronized LedgerRow update(
      String url, String project, String taskId, UnaryOperator<LedgerRow> change) {
    List<LedgerRow> kept = new ArrayList<>(rows());
    LedgerRow before =
        kept.stream()
            .filter(row -> row.url().equals(url))
            .findFirst()
            .orElseGet(() -> LedgerRow.of(url, project, taskId));
    LedgerRow after = change.apply(before);
    kept.remove(before);
    kept.add(after);
    write(kept);
    return after;
  }

  private void write(List<LedgerRow> rows) {
    try {
      AtomicFiles.write(
          paths.codeReviewLedger(),
          MAPPER.writeValueAsString(rows).getBytes(StandardCharsets.UTF_8));
    } catch (IOException unwritten) {
      throw new UncheckedIOException(unwritten);
    }
  }

  private static void aside(Path file, JacksonException unparsed) {
    Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt");
    try {
      Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException unmoved) {
      throw new UncheckedIOException(unmoved);
    }
    log.atWarn()
        .setMessage("code review ledger unreadable")
        .addKeyValue("file", file)
        .addKeyValue("cause", unparsed.getOriginalMessage())
        .addKeyValue("effect", "it moved aside and the ledger starts empty")
        .log();
  }
}

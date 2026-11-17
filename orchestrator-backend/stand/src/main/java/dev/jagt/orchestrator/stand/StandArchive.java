package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Function;

public record StandArchive(
    Path runs,
    Path recordings,
    Path failed,
    BiConsumer<String, String> report,
    Function<Path, StandTraces> traces) {

  public static StandArchive standard() {
    return new StandArchive(
        StandRunLog.DIR,
        StandRecordings.DIR,
        StandEvidence.DIR,
        (run, result) -> StandReport.append(System.getProperty("stand.report", ""), run, result),
        StandTraces::ofTheHuman);
  }
}

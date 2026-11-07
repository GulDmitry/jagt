package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.QaEnding;
import dev.jagt.orchestrator.task.QaVerdict.Status;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record QaCase(
    String name,
    String key,
    String title,
    List<Criterion> criteria,
    Deploy deploy,
    Map<String, Object> environment,
    Decline decline,
    Map<String, Status> rows,
    String report,
    String probe,
    String ending,
    List<String> names) {

  public record Criterion(String id, String text, boolean testable) {}

  public record Deploy(String sha, String landed, List<String> diff, String servedSha) {}

  public record Decline(String evidence, String why) {}

  public boolean declines() {
    return decline != null;
  }

  public QaEnding expectedEnding() {
    return QaEnding.valueOf(ending.toUpperCase(Locale.ROOT).replace('-', '_'));
  }
}

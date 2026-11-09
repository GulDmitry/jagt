package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.task.AcceptanceEnding;
import dev.jagt.orchestrator.task.AcceptanceVerdict.Status;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record AcceptanceCase(
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
    List<String> names,
    Flows flows,
    Workspace workspace) {

  public record Criterion(String id, String text, boolean testable) {}

  public record Deploy(String sha, String landed, List<String> diff, String servedSha) {}

  public record Flows(Map<String, String> files, List<String> ids) {}

  public record Workspace(Map<String, String> files, String writes) {

    public boolean held() {
      return !files.isEmpty();
    }
  }

  public record Decline(String evidence, String why) {}

  public boolean hasFlows() {
    return !flows.ids().isEmpty();
  }

  public boolean declines() {
    return decline != null;
  }

  public AcceptanceEnding expectedEnding() {
    return AcceptanceEnding.valueOf(ending.toUpperCase(Locale.ROOT).replace('-', '_'));
  }
}

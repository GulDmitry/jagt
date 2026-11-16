package dev.jagt.orchestrator.port.host;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Every path under the data root; the only place one is built. */
public class Layout {

  private final Path data;
  private final Path stateFile;

  public Layout(Path data, @Nullable Path stateFile) {
    this.data = data;
    this.stateFile = stateFile != null ? stateFile : data.resolve("state.json");
  }

  public Path data() {
    return data;
  }

  public Path stateFile() {
    return stateFile;
  }

  public Path worktrees() {
    return data.resolve("worktrees");
  }

  public Path clone(String projectKey) {
    return data.resolve("repos").resolve(projectKey);
  }

  public Path actStopped() {
    return beside("act-stopped");
  }

  public Path boardStopped() {
    return beside("board-stopped");
  }

  public Path actStrikes() {
    return beside("act-strikes");
  }

  public Path masterToken() {
    return beside("master-token");
  }

  public Path alive() {
    return beside("alive");
  }

  public Path intakeTurnedAway() {
    return beside("intake-turned-away");
  }

  public Path checkerVerdicts(String role) {
    return beside(role + "-verdicts");
  }

  public Path legacyQaVerdicts() {
    return beside("qa-verdicts");
  }

  public Path usageAlerts() {
    return beside("usage-alerts.properties");
  }

  public Path planBaselines() {
    return beside("plan-baselines");
  }

  public Path ticketTexts() {
    return beside("ticket-texts");
  }

  public Path scratch() {
    return beside("scratch");
  }

  public Path archive() {
    return beside("archive");
  }

  public Path openCodeGate() {
    return beside("opencode-gate");
  }

  public Path usageByDay() {
    return beside("usage-by-day.jsonl");
  }

  public Path finished() {
    return beside("finished.jsonl");
  }

  public Path misses() {
    return beside("misses.jsonl");
  }

  public Path interactions() {
    return beside("interactions.jsonl");
  }

  public Path stops() {
    return beside("stops.jsonl");
  }

  public Path stopsReviewed() {
    return beside("stops-reviewed.jsonl");
  }

  private Path beside(String name) {
    return stateFile.resolveSibling(name);
  }
}

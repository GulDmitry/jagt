package dev.jagt.orchestrator.task.launch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A start recorded before its first slow step. {@code key} is the item's own key where the request
 * names one that is a plain word, else a generated one; {@code startedAt} is epoch millis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingStart(String key, LaunchRequest request, long startedAt, Placement placement) {

  public enum Placement {
    PLACED,
    UNPLACED
  }
}

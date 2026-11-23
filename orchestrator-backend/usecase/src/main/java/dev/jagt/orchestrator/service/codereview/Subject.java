package dev.jagt.orchestrator.service.codereview;

/**
 * One request the code reviewer reads: where it lives, the repository's address, and the task that
 * holds it, blank for a request no task holds.
 */
public record Subject(String project, String url, String remoteUrl, String taskId) {

  public boolean own() {
    return !taskId.isBlank();
  }
}

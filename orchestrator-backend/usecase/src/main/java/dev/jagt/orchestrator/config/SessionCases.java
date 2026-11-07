package dev.jagt.orchestrator.config;

import org.springframework.stereotype.Component;

/** A case the human's copy lacks is read from the shipped file. */
@Component
public class SessionCases {

  public static final String FILE = "roles/session/cases.md";

  private final RoleFiles files;

  public SessionCases(OrchestratorPaths paths) {
    this.files = new RoleFiles(paths, "session");
  }

  public CaseBook book() {
    return files.book();
  }
}

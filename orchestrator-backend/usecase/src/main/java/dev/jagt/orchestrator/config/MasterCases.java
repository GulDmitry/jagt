package dev.jagt.orchestrator.config;

import org.springframework.stereotype.Component;

/** A case the human's copy lacks is read from the shipped file. */
@Component
public class MasterCases {

  public static final String FILE = "roles/master/cases.md";

  private final RoleFiles files;

  public MasterCases(OrchestratorPaths paths) {
    this.files = new RoleFiles(paths, "master");
  }

  public CaseBook book() {
    return files.moves();
  }
}

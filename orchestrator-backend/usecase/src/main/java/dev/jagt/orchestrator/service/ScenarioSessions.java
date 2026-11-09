package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.RoleFiles;
import dev.jagt.orchestrator.config.RoleOptions;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.CheckerReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The interactive acceptance session of a project, in the workspace the role writes, with the
 * write and shell reach its read has. Its ending is logged by its window.
 */
@Service
@RequiredArgsConstructor
public class ScenarioSessions {

  static final String SCENARIOS = "scenarios";

  private final RoleSettings settings;
  private final RoleFiles brief;
  private final ScenarioWindows windows;

  /** {@code loaded} is what the session is told beside the brief; blank when nothing. */
  public String open(String project, String loaded) {
    RoleOptions options = settings.load();
    if (!options.enabled()) {
      throw Refusal.byState("acceptance is off: set roles.acceptance.enabled to open its session");
    }
    CheckerReader.Workspace workspace =
        CheckerReach.workspace(options)
            .orElseThrow(
                () ->
                    Refusal.byState(
                        "acceptance holds no workspace: `workspace: none` in " + settings.file()));
    return windows.open(project, options.model(), prompt(project, loaded), workspace);
  }

  private String prompt(String project, String loaded) {
    String section = brief.section(SCENARIOS);
    return (section.isBlank() ? brief.text() : section)
        + "\n\nProject: "
        + project
        + (loaded.isBlank() ? "" : "\n" + loaded);
  }
}

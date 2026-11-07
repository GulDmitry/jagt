package dev.jagt.orchestrator.config;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.task.Step;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** A section the human's copy lacks is read from the shipped file. */
@Component
public class SessionBrief {

  public static final String FILE = "roles/session/brief.md";

  private final RoleFiles files;

  public SessionBrief(OrchestratorPaths paths) {
    this.files = new RoleFiles(paths, "session");
  }

  public Optional<String> section(Step step) {
    return files.find(step.id());
  }

  /** For a command line or a window that takes one line; empty where the brief has no such step. */
  public String line(Step step) {
    return section(step).map(text -> text.replaceAll("\\s*\\n\\s*", " ")).orElse("");
  }

  public List<Problem> unreadable() {
    return files.unreadable();
  }

  /** Every step either file holds a section for. */
  public Set<String> steps() {
    return files.steps();
  }
}

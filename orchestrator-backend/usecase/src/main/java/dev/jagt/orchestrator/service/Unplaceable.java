package dev.jagt.orchestrator.service;

import java.util.Collection;
import java.util.List;

/** A plan names repositories that cannot be cut: the human adds them, then approves again. */
public class Unplaceable extends RuntimeException {

  private final List<String> projects;
  private final String question;
  private final String revision;

  public Unplaceable(List<String> projects, Collection<String> registered) {
    super("The plan names " + String.join(", ", projects) + ", which jagt cannot place");
    this.projects = List.copyOf(projects);
    this.question =
        getMessage()
            + ": add each with the add-project form (a typed base branch must exist on its origin),"
            + " then approve the plan again.";
    this.revision =
        getMessage()
            + "; registered: "
            + String.join(", ", registered)
            + ". Revise the plan's Repositories section to name the projects to change.";
  }

  public String revision() {
    return revision;
  }

  public String question() {
    return question;
  }

  public List<String> projects() {
    return projects;
  }
}

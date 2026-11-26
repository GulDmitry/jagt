package dev.jagt.orchestrator.protocol;

import java.util.List;
import lombok.With;

/**
 * What a rule may ask about the world besides the message itself. Passed in rather than looked up:
 * the rules stay where nothing reads state, and whoever can answer supplies the answer.
 */
public record MessageContext(
    List<String> projectsOnTask,
    boolean planRequired,
    List<String> configuredProjects,
    @With List<String> switchesOn,
    @With boolean firstReport,
    @With boolean visualMissing,
    @With boolean waitsRefused) {

  /** Nothing is known about the task yet, so the rules that need it say nothing. */
  public static final MessageContext NONE = new MessageContext(List.of(), false, List.of());

  public MessageContext {
    projectsOnTask = projectsOnTask == null ? List.of() : List.copyOf(projectsOnTask);
    configuredProjects = configuredProjects == null ? List.of() : List.copyOf(configuredProjects);
    switchesOn = switchesOn == null ? List.of() : List.copyOf(switchesOn);
  }

  public MessageContext(
      List<String> projectsOnTask, boolean planRequired, List<String> configuredProjects) {
    this(projectsOnTask, planRequired, configuredProjects, List.of(), false, false, false);
  }

  /** Nothing here waits, so a report asking for a wait is refused. */
  public MessageContext refusingWaits() {
    return withWaitsRefused(true);
  }
}

package dev.jagt.orchestrator.protocol;

import java.util.List;

/**
 * What a rule may ask about the world besides the message itself. Passed in rather than looked up: the rules stay
 * where nothing reads state, and whoever can answer supplies the answer.
 */
public record MessageContext(List<String> projectsOnTask, boolean planRequired, List<String> configuredProjects,
                             List<String> switchesOwed) {

    /** Nothing is known about the task yet, so the rules that need it say nothing. */
    public static final MessageContext NONE = new MessageContext(List.of(), false, List.of());

    public MessageContext(List<String> projectsOnTask, boolean planRequired, List<String> configuredProjects) {
        this(projectsOnTask, planRequired, configuredProjects, List.of());
    }

    /** The switches the message must still be judged on: those that are on, for a task whose first report this is. */
    public MessageContext withSwitchesOwed(List<String> switchesOwed) {
        return new MessageContext(projectsOnTask, planRequired, configuredProjects, switchesOwed);
    }

    public MessageContext {
        projectsOnTask = projectsOnTask == null ? List.of() : List.copyOf(projectsOnTask);
        configuredProjects = configuredProjects == null ? List.of() : List.copyOf(configuredProjects);
        switchesOwed = switchesOwed == null ? List.of() : List.copyOf(switchesOwed);
    }
}

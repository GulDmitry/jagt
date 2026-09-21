package dev.jagt.orchestrator.protocol;

import java.util.List;

/**
 * What a rule may ask about the world besides the message itself. Passed in rather than looked up: the rules stay
 * where nothing reads state, and whoever can answer supplies the answer.
 */
public record MessageContext(List<String> projectsOnTask) {

    /** Nothing is known about the task yet, so the rules that need it say nothing. */
    public static final MessageContext NONE = new MessageContext(List.of());

    public MessageContext {
        projectsOnTask = projectsOnTask == null ? List.of() : List.copyOf(projectsOnTask);
    }
}

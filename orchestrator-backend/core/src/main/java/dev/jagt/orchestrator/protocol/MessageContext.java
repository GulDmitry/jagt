package dev.jagt.orchestrator.protocol;

import java.util.List;

/**
 * What a rule may ask about the world besides the message itself. Passed in rather than looked up: the rules stay
 * where nothing reads state, and whoever can answer supplies the answer.
 */
public record MessageContext(List<String> projectsOnTask, boolean planRequired, List<String> configuredProjects,
                             List<String> switchesOn, boolean firstReport, boolean visualMissing,
                             boolean waitsRefused) {

    /** Nothing is known about the task yet, so the rules that need it say nothing. */
    public static final MessageContext NONE = new MessageContext(List.of(), false, List.of());

    public MessageContext(List<String> projectsOnTask, boolean planRequired, List<String> configuredProjects) {
        this(projectsOnTask, planRequired, configuredProjects, List.of(), false, false, false);
    }

    /** Nothing here waits, so a report asking for a wait is refused. */
    public MessageContext refusingWaits() {
        return new MessageContext(projectsOnTask, planRequired, configuredProjects, switchesOn, firstReport,
                visualMissing, true);
    }

    /** The switches that are on, which the message judges itself on when it is the first report or hands back a plan. */
    public MessageContext withSwitchesOn(List<String> switchesOn) {
        return new MessageContext(projectsOnTask, planRequired, configuredProjects, switchesOn, firstReport,
                visualMissing, waitsRefused);
    }

    public MessageContext withFirstReport(boolean firstReport) {
        return new MessageContext(projectsOnTask, planRequired, configuredProjects, switchesOn, firstReport,
                visualMissing, waitsRefused);
    }

    /** The task's worktree holds no picture; unknown reads as not missing. */
    public MessageContext withVisualMissing(boolean visualMissing) {
        return new MessageContext(projectsOnTask, planRequired, configuredProjects, switchesOn, firstReport,
                visualMissing, waitsRefused);
    }

    public MessageContext {
        projectsOnTask = projectsOnTask == null ? List.of() : List.copyOf(projectsOnTask);
        configuredProjects = configuredProjects == null ? List.of() : List.copyOf(configuredProjects);
        switchesOn = switchesOn == null ? List.of() : List.copyOf(switchesOn);
    }
}

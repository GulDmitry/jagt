package dev.jagt.orchestrator.port;

/**
 * Something a human must be told. A record rather than a title and a body, because routing needs to know what
 * kind of thing this is: an orphaned directory and an agent asking a question are both worth attention, and only
 * one of them belongs in an inbox.
 */
public record Notification(Topic topic, Severity severity, String taskId, String title, String body) {

    /** What the notification is ABOUT. A channel takes or leaves a notification by this, not by its wording. */
    public enum Topic {
        /** The agent needs the human: a question, a report, a round that ended. */
        AGENT,
        /** An agent that should be working has gone quiet. */
        WATCHDOG,
        /** Nothing is wrong with the work — something on disk or in the install wants a human eventually. */
        HOUSEKEEPING,
        /** jagt itself cannot go on correctly until the human acts. */
        INSTALL
    }

    /** How loud, independent of what it is about. */
    public enum Severity { INFO, ATTENTION, ALERT }

    public static Notification fromAgent(String taskId, String title, String body) {
        return new Notification(Topic.AGENT, Severity.ATTENTION, taskId, title, body);
    }

    /** The checks went red on a task out for review — nobody is watching the host, so this is how it arrives. */
    public static Notification checksFailed(String taskId, String hostStatus) {
        return new Notification(Topic.AGENT, Severity.ATTENTION, taskId, "checks " + hostStatus,
                "the pipeline is not green — `sweep` relays the failure to the agent");
    }

    public static Notification watchdog(String taskId, String title, String body) {
        return new Notification(Topic.WATCHDOG, Severity.ALERT, taskId, title, body);
    }

    public static Notification housekeeping(String title, String body) {
        return new Notification(Topic.HOUSEKEEPING, Severity.INFO, null, title, body);
    }

    public static Notification install(String title, String body) {
        return new Notification(Topic.INSTALL, Severity.ALERT, null, title, body);
    }
}

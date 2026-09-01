package dev.jagt.orchestrator.port;

/** Something a human must be told, carrying what KIND of thing it is so a channel can route on that. */
public record Notification(Topic topic, String taskId, String title, String body) {

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

    public static Notification fromAgent(String taskId, String title, String body) {
        return new Notification(Topic.AGENT, taskId, title, body);
    }

    /** Nobody is watching the host, so a run that went red arrives this way. */
    public static Notification checksFailed(String taskId, String hostStatus) {
        return new Notification(Topic.AGENT, taskId, "checks " + hostStatus,
                "the pipeline is not green — `sweep` relays the failure to the agent");
    }

    public static Notification watchdog(String taskId, String title, String body) {
        return new Notification(Topic.WATCHDOG, taskId, title, body);
    }

    public static Notification housekeeping(String title, String body) {
        return new Notification(Topic.HOUSEKEEPING, null, title, body);
    }

    public static Notification install(String title, String body) {
        return new Notification(Topic.INSTALL, null, title, body);
    }
}

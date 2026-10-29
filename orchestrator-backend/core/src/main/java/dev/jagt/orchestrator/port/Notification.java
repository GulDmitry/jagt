package dev.jagt.orchestrator.port;

import lombok.With;
import org.jspecify.annotations.Nullable;

/** Something a human must be told, carrying what KIND of thing it is so a channel can route on that. */
public record Notification(Topic topic, @Nullable String taskId, @With @Nullable String alias, String title, String body) {

    /** What the notification is ABOUT. A channel takes or leaves a notification by this, not by its wording. */
    public enum Topic {
        /** The agent needs the human: a question, a report, a round that ended. */
        AGENT,
        /** An agent that should be working has gone quiet. */
        WATCHDOG,
        /** Nothing is wrong with the work — something on disk or in the install wants a human eventually. */
        HOUSEKEEPING,
        /** jagt itself cannot go on correctly until the human acts. */
        INSTALL,
        /** A subscription window filled up, or paid usage is being spent. */
        USAGE
    }

    public static Notification fromAgent(@Nullable String taskId, String title, String body) {
        return new Notification(Topic.AGENT, taskId, null, title, body);
    }

    public static Notification watchdog(String taskId, String title, String body) {
        return new Notification(Topic.WATCHDOG, taskId, null, title, body);
    }

    public static Notification housekeeping(String title, String body) {
        return new Notification(Topic.HOUSEKEEPING, null, null, title, body);
    }

    public static Notification install(String title, String body) {
        return new Notification(Topic.INSTALL, null, null, title, body);
    }

    public static Notification usage(String title, String body) {
        return new Notification(Topic.USAGE, null, null, title, body);
    }
}

package dev.jagt.orchestrator.port;

import lombok.With;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/** Something a human must be told, carrying what KIND of thing it is so a channel can route on that. */
public record Notification(Topic topic, @With Event event, @Nullable String taskId, @With @Nullable String alias,
                           String title, String body) {

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

    /** The occasion that raises a notification: what a channel's configuration chooses by. */
    public enum Event {
        YOUR_MOVE("your-move", true),
        AUTO_REVIEW("auto-review", true),
        STOPPED("stopped", true),
        HOUSEKEEPING("housekeeping", true),
        INSTALL("install", true),
        USAGE("usage", true),
        REQUEST_OPENED("request-opened", true),
        DEPLOYED("deployed", false);

        private final String id;
        private final boolean onByDefault;

        Event(String id, boolean onByDefault) {
            this.id = id;
            this.onByDefault = onByDefault;
        }

        public String id() {
            return id;
        }

        public boolean onByDefault() {
            return onByDefault;
        }

        public static Optional<Event> ofId(String id) {
            return Arrays.stream(values()).filter(event -> event.id.equals(id)).findFirst();
        }
    }

    public static Notification fromAgent(@Nullable String taskId, String title, String body) {
        return new Notification(Topic.AGENT, Event.YOUR_MOVE, taskId, null, title, body);
    }

    public static Notification watchdog(String taskId, String title, String body) {
        return new Notification(Topic.WATCHDOG, Event.STOPPED, taskId, null, title, body);
    }

    public static Notification housekeeping(String title, String body) {
        return new Notification(Topic.HOUSEKEEPING, Event.HOUSEKEEPING, null, null, title, body);
    }

    public static Notification install(String title, String body) {
        return new Notification(Topic.INSTALL, Event.INSTALL, null, null, title, body);
    }

    public static Notification usage(String title, String body) {
        return new Notification(Topic.USAGE, Event.USAGE, null, null, title, body);
    }

    public static Notification requestOpened(String taskId, String body) {
        return new Notification(Topic.AGENT, Event.REQUEST_OPENED, taskId, null, "request opened", body);
    }

    public static Notification deployed(String taskId, String body) {
        return new Notification(Topic.AGENT, Event.DEPLOYED, taskId, null, "deployed", body);
    }
}

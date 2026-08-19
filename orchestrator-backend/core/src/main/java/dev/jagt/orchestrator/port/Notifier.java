package dev.jagt.orchestrator.port;


/**
 * One way of reaching the human. Adding one must change no caller, so nothing here describes a transport: a
 * channel says which notifications it wants and delivers them.
 *
 * <p>Contract: never throw, and never block for long. A channel that cannot deliver logs and returns; the
 * dispatcher gives it a thread of its own so a slow one cannot delay the work that emitted the notification.
 */
public interface Notifier {

    /** Short and stable — it names the channel in a log line and in configuration. */
    String id();

    /** Whether this channel wants it. Default: everything. */
    default boolean takes(Notification notification) {
        return true;
    }

    void deliver(Notification notification);
}

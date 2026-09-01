package dev.jagt.orchestrator.port;


/**
 * One way of reaching the human: a channel says which notifications it wants and delivers them, so nothing here
 * describes a transport. Never throws and never blocks for long — the dispatcher gives it a thread of its own.
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

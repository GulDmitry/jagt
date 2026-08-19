package dev.jagt.orchestrator.port;

/**
 * A banner on the machine the human is sitting at, selected by {@code orchestrator.platform}.
 *
 * <p>Contract: fire-and-forget. A notification is best-effort, so an implementation swallows and logs a delivery
 * failure and never throws — a banner nobody saw must not be able to stop the work that raised it.
 */
public interface UserNotifier {

    void notify(String title, String message);
}

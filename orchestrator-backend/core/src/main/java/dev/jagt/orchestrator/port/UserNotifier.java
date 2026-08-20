package dev.jagt.orchestrator.port;

/**
 * A banner on the machine the human is sitting at, selected by {@code orchestrator.platform}.
 *
 * <p>Contract: fire-and-forget. A notification is best-effort, so an implementation swallows and logs a delivery
 * failure and never throws — a banner nobody saw must not be able to stop the work that raised it.
 */
public interface UserNotifier {

    /**
     * @param link a URL to open when the human CLICKS the banner, or null. A banner says which task needs them
     *             and then leaves them to find it; the click is what closes that gap. Optional in the strong
     *             sense: a platform whose banners carry no action ignores it, and no caller may depend on it
     */
    void notify(String title, String message, String link);
}

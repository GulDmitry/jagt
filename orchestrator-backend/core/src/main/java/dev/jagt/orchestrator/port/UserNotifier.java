package dev.jagt.orchestrator.port;

/**
 * A banner on the machine the human is sitting at, selected by {@code orchestrator.platform}. Fire-and-forget: a
 * failed delivery is logged, never thrown.
 */
public interface UserNotifier {

    /**
     * {@code link} is a URL to open when the human CLICKS the banner, or null — a platform whose banners carry no
     * action ignores it, and no caller may depend on it.
     */
    void notify(String title, String message, String link);
}

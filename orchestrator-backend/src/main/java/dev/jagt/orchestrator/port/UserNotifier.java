package dev.jagt.orchestrator.port;

/**
 * OS notification strategy. Selected via {@code orchestrator.platform}
 * (default: macos → osascript). A Linux impl would use notify-send.
 *
 * <p>Contract: fire-and-forget. Callers (Watchdog, MCP tools, agents pinging
 * the human) treat notifications as best-effort UX — implementations must
 * swallow and log delivery failures, never throw: a broken notification must
 * not break orchestration.
 */
public interface UserNotifier {

    void notify(String title, String message);
}

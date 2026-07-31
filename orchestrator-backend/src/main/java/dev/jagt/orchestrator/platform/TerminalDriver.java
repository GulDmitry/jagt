package dev.jagt.orchestrator.platform;

import java.nio.file.Path;

/**
 * Terminal application strategy: how the agents' tmux sessions become visible
 * to the human. Selected via {@code orchestrator.terminal} (default: warp).
 *
 * <p>Invariant the orchestrator relies on: ONE dedicated agents window. Task
 * views open as tabs inside it; a window is created only when none exists.
 */
public interface TerminalDriver {

    /**
     * Makes the tmux session visible. Called only when {@code tmux list-clients}
     * shows nobody attached to the session — the driver does not need to check.
     *
     * <p>Contract: if a window whose title starts with {@code dedicatedTitle}
     * exists, add a new TAB to it; otherwise create the dedicated window. The
     * opened tab/window must end up attached to {@code tmuxSession} (the Warp
     * impl drops a one-shot {@code .jagt_tab} marker into {@code tabCwd} and
     * relies on a shell-rc hook that execs {@code tmux attach}).
     * Must debounce internally: the UI needs seconds to open and attach, and
     * the orchestrator may call again meanwhile. Failures: log, don't throw.
     *
     * @param tmuxSession    tmux session the view must attach to
     * @param dedicatedTitle title prefix identifying the agents window
     *                       (= the base tmux session name)
     * @param tabCwd         directory the tab starts in (worktree or root)
     */
    void openViewer(String tmuxSession, String dedicatedTitle, Path tabCwd);

    /** Brings the terminal application to the foreground. Best-effort. */
    void bringToFront();

    /**
     * Brings the agents viewer to the user's screen — raises the window whose
     * title starts with {@code dedicatedTitle} (addressed, no keystrokes) and
     * activates the app. Returns true if such a window was found and raised.
     * Note: a terminal with no tab-selection API (e.g. Warp) can raise a WINDOW
     * but cannot switch to a specific TAB — so the viewer must be its own window
     * for this to land on the right task. Best-effort; never throws.
     */
    boolean reveal(String dedicatedTitle);

    /**
     * Closes the dedicated agents window(s) matching {@code dedicatedTitle}.
     * Called when the LAST task is removed — end-of-day cleanup. Individual
     * tabs need not (and on Warp cannot) be closed programmatically; dead
     * mid-flight tabs are closed by the human. Failures: log, don't throw.
     */
    void closeViewerWindow(String dedicatedTitle);
}

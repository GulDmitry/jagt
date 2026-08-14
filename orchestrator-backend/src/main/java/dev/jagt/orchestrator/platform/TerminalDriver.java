package dev.jagt.orchestrator.platform;

import java.nio.file.Path;

/**
 * How the agents' tmux sessions become visible, selected by {@code orchestrator.terminal}.
 *
 * <p>The invariant every implementation owes: ONE dedicated agents window, task views as tabs inside it.
 */
public interface TerminalDriver {

    /**
     * Makes the tmux session visible; need not check whether anyone is attached already. Add a TAB when a
     * window titled {@code dedicatedTitle} exists, else create that window, and attach it to
     * {@code tmuxSession}. Debounce internally — opening takes seconds and this may be called again meanwhile.
     * Log failures, never throw.
     *
     * @param tmuxSession    tmux session the view must attach to
     * @param dedicatedTitle title prefix identifying the agents window
     * @param tabCwd         directory the tab starts in
     */
    void openViewer(String tmuxSession, String dedicatedTitle, Path tabCwd);

    /** Brings the terminal application to the foreground. Best-effort. */
    void bringToFront();

    /**
     * Raises the window titled {@code dedicatedTitle} — addressed, never by keystroke — and activates the app.
     * True if such a window was found. A driver that cannot select a TAB raises the window and returns true.
     * Best-effort; never throws.
     */
    boolean reveal(String dedicatedTitle);

    /**
     * Closes the agents window(s) titled {@code dedicatedTitle}. Individual tabs need not be closed — some
     * terminals cannot. Log failures, never throw.
     */
    void closeViewerWindow(String dedicatedTitle);
}

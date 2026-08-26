package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/**
 * How the agents' sessions become visible, selected by {@code orchestrator.terminal}.
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
     * What a {@link #reveal(String)} achieved — the driver's own answer, because "nothing came forward" has more
     * than one reason and only the driver knows which. A caller that has to pick a sentence for a boolean picks
     * one that is right for a single terminal and a lie in the next.
     */
    enum Revealed {
        /** The agents window is in front. */
        WINDOW,
        /** The viewer is a TAB behind another one, and this terminal has no API to select a tab. */
        UNREACHABLE_TAB,
        /** No viewer is open at all. */
        NOT_RUNNING
    }

    /**
     * Raises the window titled {@code dedicatedTitle} — addressed, never by keystroke — and activates the app.
     * Best-effort; never throws.
     */
    Revealed reveal(String dedicatedTitle);

    /**
     * Closes the agents window(s) titled {@code dedicatedTitle}. Individual tabs need not be closed — some
     * terminals cannot. Log failures, never throw.
     */
    void closeViewerWindow(String dedicatedTitle);
}

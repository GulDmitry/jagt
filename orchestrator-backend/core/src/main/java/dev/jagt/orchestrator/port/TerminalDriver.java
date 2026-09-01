package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/** How the agents' sessions become visible: ONE dedicated agents window, task views as tabs inside it. */
public interface TerminalDriver {

    /**
     * Adds a tab to the window titled {@code dedicatedTitle}, creating that window when it is absent, attached to
     * {@code attachTarget}. Debounce internally — opening takes seconds and this may be called again meanwhile.
     * Logs failures, never throws.
     */
    void openViewer(String attachTarget, String dedicatedTitle, Path tabCwd);

    /** Brings the terminal application to the foreground. Best-effort. */
    void bringToFront();

    /** What a {@link #reveal(String)} achieved. */
    enum Revealed {
        /** The agents window is in front. */
        WINDOW,
        /** The viewer is a TAB behind another one, and this terminal has no API to select a tab. */
        UNREACHABLE_TAB,
        /** No viewer is open at all. */
        NOT_RUNNING
    }

    /** Raises the window titled {@code dedicatedTitle}, addressed rather than by keystroke. Never throws. */
    Revealed reveal(String dedicatedTitle);

    /** Closes the agents window(s) titled {@code dedicatedTitle}; tabs need not close, some terminals cannot. */
    void closeViewerWindow(String dedicatedTitle);
}

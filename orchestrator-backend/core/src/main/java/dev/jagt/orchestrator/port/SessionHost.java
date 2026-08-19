package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/**
 * Where an agent's session LIVES: something that keeps a process running after whoever started it walks away, and
 * lets a human attach to it later. This is not the same thing as a {@link TerminalDriver} — that one only shows a
 * session to a human, and the two are swapped independently (a browser terminal shows the very same session a
 * native window does).
 *
 * <p>The model is two levels, because every host that survives a detach has them: one SESSION holds a WINDOW per
 * task. A host without that shape maps a window onto whatever it does have — a detached process, a container exec
 * — as long as the window can be found again by the task's id, which is the only handle anything above here uses.
 */
public interface SessionHost {

    /** What is happening in a task's window right now. */
    enum WindowState {
        /** No window by that name — nothing was ever started, or it closed itself. */
        MISSING,
        /** The window is there but the agent is gone; what is left is the shell it ran in. */
        DEAD_SHELL,
        /** The agent is running. */
        AGENT_RUNNING
    }

    /** The session name to use, given whatever the human configured (blank included). */
    String sessionName(String configured);

    /**
     * Starts a task's agent in its own window, replacing any window of the same name — one task is one window, so a
     * respawn must not accumulate duplicates.
     */
    void openTaskWindow(String session, String dedicatedTitle, String taskId, String alias, Path worktreePath,
                        boolean planMode);

    /** Brings the task's window to whoever is attached; false when there is no such window. */
    boolean focusTaskWindow(String session, String dedicatedTitle, String taskId);

    WindowState taskWindowState(String session, String taskId);

    /** Types a line into the session as if a human had, without touching focus; false when it is gone. */
    boolean nudgeTaskWindow(String session, String taskId, String message);

    /** Closes every window a task owns, and answers how many there were. */
    int killTaskWindows(String session, String taskId);

    /** When the window last showed any activity, as epoch millis — 0 when unknown. */
    long lastWindowActivityMillis(String session, String taskId);
}

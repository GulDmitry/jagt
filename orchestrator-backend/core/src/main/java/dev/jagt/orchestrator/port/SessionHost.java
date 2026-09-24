package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/**
 * Where an agent's session LIVES: something keeping a process running after whoever started it walks away, and
 * letting a human attach later. Not a {@link TerminalDriver}, which only shows a session to a human; the two are
 * swapped independently.
 *
 * <p>The model is two levels: one SESSION holds WINDOWS, usually one per task. A host without that shape maps a
 * window onto whatever it has, as long as the window can be found again by its name.
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

    /** Starts a task's agent in its own window, replacing any window of the same name. */
    void openTaskWindow(String session, String dedicatedTitle, String taskId, String alias, Path worktreePath,
                        boolean planMode);

    /**
     * Starts a command in a window of its own, replacing any window of that name. What runs there is the
     * caller's; every other method here finds the window again by the same name, task or not.
     */
    void openWindow(String session, String dedicatedTitle, String name, Path cwd, String command);

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

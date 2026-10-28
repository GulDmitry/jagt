package dev.jagt.orchestrator.port;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

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

    /** The session's windows could not be listed, which says nothing about whether one exists; the cause is in the message. */
    class WindowsUnreadable extends IllegalStateException {

        public WindowsUnreadable(String message) {
            super(message);
        }
    }

    /** The session name to use, given whatever the human configured (blank included). */
    String sessionName(@Nullable String configured);

    /** Why the host cannot address a session by this name, empty where it can. */
    default Optional<String> unaddressable(String name) {
        return Optional.empty();
    }

    /** Starts a task's agent in its own window, replacing any window of the same name; a blank model is the default. */
    void openTaskWindow(String session, String dedicatedTitle, String taskId, @Nullable String alias, Path worktreePath,
                        boolean planMode, String model);

    /**
     * Re-enters a task's window after the session behind it was lost — a killed session server, a rebooted machine —
     * keeping what that session knew where the runtime can. A human asking for a restart gets {@link
     * #openTaskWindow} instead.
     */
    void reviveTaskWindow(String session, String dedicatedTitle, String taskId, @Nullable String alias, Path worktreePath);

    /**
     * A window following a file as it grows, opened where it is missing and left alone where it is there or where
     * the session's windows cannot be listed.
     */
    void keepFeedWindow(String session, String name, Path file);

    /** Brings the task's window to whoever is attached; false when there is no such window. */
    boolean focusTaskWindow(String session, String dedicatedTitle, String taskId);

    /** A session that cannot list its windows throws: unknown is never {@link WindowState#MISSING}. */
    WindowState taskWindowState(String session, String taskId);

    /**
     * What the task's window shows, its styling kept; empty when it is gone, a throw when it is there but unread.
     * A failed read throws as every adapter read does; the three answers are a value, empty and the throw.
     */
    Optional<String> screenOf(String session, String taskId);

    /**
     * The last lines an agent printed before it exited, while its window still shows them; empty while it runs or
     * once the window is gone. A failed read throws.
     */
    Optional<String> exitTail(String session, String taskId);

    /** Types a line into the session as if a human had, without touching focus; false when it is gone. */
    boolean nudgeTaskWindow(String session, String taskId, String message);

    /** Presses Enter in the task's window, sending what its input holds; false when it is gone. */
    boolean submitTaskWindow(String session, String taskId);

    /** Closes every window a task owns, and answers how many there were; a throw when they cannot be listed. */
    int killTaskWindows(String session, String taskId);

    /** When the window last showed any activity, as epoch millis — 0 when there is none; a throw when unreadable. */
    long lastWindowActivityMillis(String session, String taskId);
}

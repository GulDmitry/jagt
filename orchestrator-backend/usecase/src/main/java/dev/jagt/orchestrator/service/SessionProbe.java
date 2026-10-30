package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;

/**
 * What can be read of a task's session without a hook, none of it costing a token.
 */
@Component
@RequiredArgsConstructor
public class SessionProbe {

    /** What a session's harness says about it; a turn ending says only that a turn ended. */
    public enum State { GONE, FAILED, IDLE, WORKING }

    private final ConfigService configService;
    private final AgentSessions sessions;
    private final AgentRuntime runtime;

    /** Read per run, so a changed interval applies without a restart. */
    public Duration every() {
        return Duration.ofSeconds(config().agent().probeSecondsOrDefault());
    }

    /**
     * Never allowed to fail: one read is asked on the scheduler's own thread, where a throw strands the job rather
     * than skipping a run, and the file is unreadable for exactly as long as a human takes to save it.
     */
    private ConfigService.ConfigFile config() {
        try {
            return configService.load();
        } catch (RuntimeException e) {
            return ConfigService.ConfigFile.defaults();
        }
    }

    /**
     * The window's process is read on every look, as nothing else tells a dead one; what it printed costs a second
     * spawn and is asked only once the free signs read stale.
     */
    public SessionSign.Read read(String taskId, TaskState task, long staleMs, long now) {
        long log = ownLog(task);
        SessionHost.WindowState process = sessions.windowState(taskId);
        if (process != SessionHost.WindowState.AGENT_RUNNING) {
            return new SessionSign.Read(gone(process), 0, log, task.lastActiveTimestamp(), staleMs, exitTail(taskId));
        }
        Session session = task.session();
        Session.Halt halt = session.halt();
        long lastSign = Math.max(Math.max(task.lastActiveTimestamp(), log),
                Math.max(session.movedAt(), halt == null ? 0 : halt.at()));
        boolean verdictStands = halt != null && halt.kind() != Session.Halt.Kind.IDLE;
        long printed = verdictStands || now - lastSign < staleMs ? 0 : sessions.printedAt(taskId);
        return new SessionSign.Read(Session.Window.State.RUNNING, printed, log, task.lastActiveTimestamp(), staleMs, null);
    }

    private static Session.Window.State gone(SessionHost.WindowState process) {
        return process == SessionHost.WindowState.MISSING ? Session.Window.State.MISSING : Session.Window.State.DEAD_SHELL;
    }

    /** What the agent printed on its way out; a window that cannot be captured only costs the words. */
    private @Nullable String exitTail(String taskId) {
        try {
            return sessions.exitTail(taskId).filter(printed -> !printed.isBlank()).map(OneLine::clipped).orElse(null);
        } catch (RuntimeException unread) {
            return null;
        }
    }

    /**
     * The LATER of what a session named and what the runtime finds. Preferring the named file would freeze the sign
     * the moment a session is cleared or resumed, the work having moved to a new file.
     */
    private long ownLog(TaskState task) {
        String named = task.session().log();
        String worktree = task.worktreePath();
        long derived = worktree == null || worktree.isBlank()
                ? 0
                : runtime.lastSessionActivity(Path.of(worktree)).orElse(0);
        return Math.max(derived, named == null ? 0 : FileStamps.modified(Path.of(named)));
    }
}

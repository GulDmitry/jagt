package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What can be read of a task's session without a hook, none of it costing a token. The file a session names is kept
 * in memory: losing it on a restart costs promptness and nothing else.
 */
@Component
@RequiredArgsConstructor
public class SessionProbe {

    /** What a session's harness says about it; a turn ending says only that a turn ended. */
    public enum State { GONE, FAILED, IDLE, WORKING }

    private final ConfigService configService;
    private final SessionHost sessions;
    private final AgentRuntime runtime;
    private final Map<String, Path> reportedLog = new ConcurrentHashMap<>();

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

    /** Task ids are ticket keys and get reused, so what was remembered about a closed one must not be found. */
    public void keepOnly(Collection<String> taskIds) {
        reportedLog.keySet().retainAll(taskIds);
    }

    /** A session that names the file it appends to takes the guessing out of finding it. */
    public void logAt(String taskId, Path log) {
        reportedLog.put(taskId, log);
    }

    /** The windows are asked only once the free signs have run out and already read stale: that is a process spawn. */
    public SessionSign.Read read(String taskId, TaskState task, long staleMs, long now) {
        long log = ownLog(taskId, task);
        Session session = task.session();
        Session.Halt halt = session.halt();
        long lastSign = Math.max(Math.max(task.lastActiveTimestamp(), log),
                Math.max(session.movedAt(), halt == null ? 0 : halt.at()));
        boolean verdictStands = halt != null && halt.kind() != Session.Halt.Kind.IDLE;
        long printed = verdictStands || now - lastSign < staleMs ? 0 : sessions.lastWindowActivityMillis(
                sessions.sessionName(config().viewer().session()), taskId);
        return new SessionSign.Read(printed > 0 ? Session.Window.State.RUNNING : Session.Window.State.UNREAD, printed,
                log, task.lastActiveTimestamp(), staleMs);
    }

    /**
     * The LATER of what a session named and what the runtime finds. Preferring the named file would freeze the sign
     * the moment a session is cleared or resumed, the work having moved to a new file.
     */
    private long ownLog(String taskId, TaskState task) {
        Path reported = reportedLog.get(taskId);
        String worktree = task.worktreePath();
        long derived = worktree == null || worktree.isBlank()
                ? 0
                : runtime.lastSessionActivity(Path.of(worktree)).orElse(0);
        return Math.max(derived, reported == null ? 0 : FileStamps.modified(reported));
    }
}

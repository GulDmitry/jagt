package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every sign of what a task's session is doing, and the one verdict from them. None of them costs a token or
 * asks a model: a session's own log is read, and its harness reports it.
 *
 * <p>Reports are kept in memory rather than written down. Losing them on a restart costs promptness and
 * nothing else — the log a session keeps says the same thing one threshold later.
 */
@Component
@RequiredArgsConstructor
public class SessionProbe {

    /** What a session's harness says about it. */
    public enum State { WAITING, GONE, WORKING }

    /** Since when nothing has moved, and what a harness said about it — null where none of them did. */
    public record Silence(long since, State reported) {

        /** The words a human is owed, or null where nothing was said and the sentence is the surface's own. */
        public String detail() {
            if (reported == State.GONE) {
                return "the session ended";
            }
            return reported == State.WAITING ? "waiting for input" : null;
        }
    }

    private record Halt(long at, State state) {
    }

    private final ConfigService configService;
    private final SessionHost sessions;
    private final AgentRuntime runtime;
    private final Map<String, Halt> halted = new ConcurrentHashMap<>();
    private final Map<String, Long> movingSince = new ConcurrentHashMap<>();
    private final Map<String, Path> reportedLog = new ConcurrentHashMap<>();

    /** Read per run, so a changed interval applies without a restart. */
    public Duration every() {
        return Duration.ofSeconds(config().agent().probeSecondsOrDefault());
    }

    /**
     * Never allowed to fail. One read is asked on the scheduler's own thread, where a throw strands the job
     * rather than skipping a run, and another inside a request a hook cannot see the answer to — while the file
     * itself is unreadable for exactly as long as a human takes to save it.
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
        halted.keySet().retainAll(taskIds);
        movingSince.keySet().retainAll(taskIds);
        reportedLog.keySet().retainAll(taskIds);
    }

    /**
     * A session reporting itself alive DROPS what was said before rather than outranking it by time: two hooks
     * of one event (an end and the start that follows it) are stamped on arrival and can share a millisecond
     * or arrive out of order, and a lost alarm is cheaper than one nobody can clear.
     */
    public void report(String taskId, State state, long at) {
        if (state == State.WORKING) {
            movingSince.put(taskId, at);
            halted.remove(taskId);
            return;
        }
        halted.put(taskId, new Halt(at, state));
    }

    /** A session that names the file it appends to takes the guessing out of finding it. */
    public void logAt(String taskId, Path log) {
        reportedLog.put(taskId, log);
    }

    /**
     * A report needs no threshold — it is what happened, not an absence — so it answers whenever it is the most
     * recent sign. Anything newer supersedes it, which is how answering a session stops the alarm.
     */
    public Optional<Silence> of(String taskId, TaskState task, long staleMs, long now) {
        long ownLog = ownLog(taskId, task);
        long lastSign = Math.max(Math.max(task.lastActiveTimestamp(), ownLog),
                movingSince.getOrDefault(taskId, 0L));
        Halt halt = halted.get(taskId);
        if (halt != null && halt.at() >= lastSign) {
            return Optional.of(new Silence(halt.at(), halt.state()));
        }
        if (halt != null) {
            // A tie KEEPS it: a turn is appended to the log immediately before the hook that reports it fires,
            // and both are stamped in whichever millisecond they land in. Dropped only by a strictly newer
            // sign, and only if it is still the one just read, or a report landing meanwhile is lost unread.
            halted.remove(taskId, halt);
        }
        lastSign = Math.max(lastSign, windowActivity(taskId, lastSign, staleMs, now));
        return now - lastSign < staleMs
                ? Optional.empty()
                : Optional.of(new Silence(lastSign, null));
    }

    /**
     * The LATER of what a session named and what the runtime finds. Preferring the named file would freeze the
     * sign the moment a session is cleared or resumed: the file it named still exists, with the mtime it had
     * when that session stopped writing to it, while the work moved to a new one.
     */
    private long ownLog(String taskId, TaskState task) {
        Path reported = reportedLog.get(taskId);
        String worktree = task.worktreePath();
        long derived = worktree == null || worktree.isBlank()
                ? 0
                : runtime.lastSessionActivityMillis(Path.of(worktree));
        return Math.max(derived, reported == null ? 0 : modified(reported));
    }

    private static long modified(Path log) {
        try {
            return Files.getLastModifiedTime(log).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * A process spawn, so it is asked only once the free signs have run out and already read stale.
     *
     * <p>It stays a sign even though it says nothing about a session at a prompt: a log gets its entry when a
     * tool call is ISSUED and nothing at all while that call runs, so an eight-minute build would otherwise
     * read as death. What a prompt looks like is the harness's to report, not this threshold's to infer.
     */
    private long windowActivity(String taskId, long lastSign, long staleMs, long now) {
        return now - lastSign < staleMs
                ? 0
                : sessions.lastWindowActivityMillis(
                        sessions.sessionName(config().viewer().tmuxSession()), taskId);
    }
}

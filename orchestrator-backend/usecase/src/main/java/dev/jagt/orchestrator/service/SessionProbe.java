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
 * Every sign of what a task's session is doing, and the one verdict from them. None costs a token. Reports are kept
 * in memory: losing them on a restart costs promptness and nothing else.
 */
@Component
@RequiredArgsConstructor
public class SessionProbe {

    /**
     * What a session's harness says about it. {@code WAITING} and {@code GONE} are verdicts on their own;
     * {@code IDLE} is not — a turn ending says only that a turn ended.
     */
    public enum State { WAITING, GONE, IDLE, WORKING }

    /** Since when nothing has moved, and what a harness said about it — null where none of them did. */
    public record Silence(long since, State reported) {

        /** The words a human is owed, or null where nothing was said and the sentence is the surface's own. */
        public String detail() {
            if (reported == State.GONE) {
                return "the session ended";
            }
            if (reported == State.IDLE) {
                return "its turn ended and nothing has moved since";
            }
            return reported == State.WAITING ? "waiting for input" : null;
        }

        /** Whether a harness put the session AT a prompt, as opposed to reporting it gone or saying nothing. */
        public boolean atAPrompt() {
            return reported == State.WAITING || reported == State.IDLE;
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
        halted.keySet().retainAll(taskIds);
        movingSince.keySet().retainAll(taskIds);
        reportedLog.keySet().retainAll(taskIds);
    }

    /**
     * A session reporting itself alive DROPS what was said before rather than outranking it by time: two hooks of
     * one event are stamped on arrival and can share a millisecond or arrive out of order.
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

    /** A report needs no threshold, being what happened rather than an absence; anything newer supersedes it. */
    public Optional<Silence> of(String taskId, TaskState task, long staleMs, long now) {
        long ownLog = ownLog(taskId, task);
        long lastSign = Math.max(Math.max(task.lastActiveTimestamp(), ownLog),
                movingSince.getOrDefault(taskId, 0L));
        Halt halt = halted.get(taskId);
        if (halt != null && halt.at() >= lastSign) {
            if (halt.state() != State.IDLE) {
                return Optional.of(new Silence(halt.at(), halt.state()));
            }
            // A turn that ENDED is a sign like any other, not a verdict: only nothing happening AFTER it means
            // anything, which is the threshold below.
            lastSign = halt.at();
        } else if (halt != null) {
            // A tie KEEPS it: a turn is appended to the log immediately before the hook reporting it fires, and
            // both are stamped in whichever millisecond they land in. Dropped only if it is still the one read.
            halted.remove(taskId, halt);
            halt = null;
        }
        long printed = windowActivity(taskId, lastSign, staleMs, now);
        if (printed > lastSign) {
            // Output AFTER the turn ended is a later sign, so the report no longer describes this wait.
            lastSign = printed;
            halted.remove(taskId, halt);
            halt = null;
        }
        return now - lastSign < staleMs
                ? Optional.empty()
                : Optional.of(new Silence(lastSign, halt == null ? null : halt.state()));
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
     * A process spawn, so it is asked only once the free signs have run out and already read stale. It stays a sign
     * even at a prompt: a log gets its entry when a tool call is ISSUED and nothing while it runs, so an
     * eight-minute build would otherwise read as death.
     */
    private long windowActivity(String taskId, long lastSign, long staleMs, long now) {
        return now - lastSign < staleMs
                ? 0
                : sessions.lastWindowActivityMillis(
                        sessions.sessionName(config().viewer().tmuxSession()), taskId);
    }
}

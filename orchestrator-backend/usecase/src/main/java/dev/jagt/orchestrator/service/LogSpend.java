package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.SessionLog;
import dev.jagt.orchestrator.task.TokenUsage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reading a growing log from where the last read stopped. Bounded and repeatable, so a log of any size costs one
 * bounded read and the rest waits for the next; where the reader keeps its mark is the caller's business.
 */
public final class LogSpend {

    /** One read takes at most this much, so a log of any size costs a bounded read. */
    private static final long WINDOW = 8L * 1024 * 1024;

    /** What the log spent since {@code mark}, and the byte the next read starts at. */
    public record Advance(TokenUsage usage, long mark) {
    }

    private LogSpend() {
    }

    /**
     * Empty means nothing to count: the log is gone, unreadable, or has grown by nothing. A log SHORTER than its
     * own mark was rewritten under jagt — what was counted cannot be told, so the mark follows the file and
     * nothing is added.
     */
    public static Optional<Advance> since(SessionLog sessionLog, Path log, long mark) {
        long size;
        try {
            size = Files.size(log);
        } catch (IOException | RuntimeException gone) {
            return Optional.empty();
        }
        if (size < mark) {
            return Optional.of(new Advance(TokenUsage.NONE, size));
        }
        if (size == mark) {
            return Optional.empty();
        }
        SessionLog.Spent spent = sessionLog.spent(log, mark, Math.min(size - mark, WINDOW));
        return spent.upTo() <= mark ? Optional.empty() : Optional.of(new Advance(spent.usage(), spent.upTo()));
    }
}

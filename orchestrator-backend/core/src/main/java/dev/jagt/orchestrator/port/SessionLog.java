package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TokenUsage;

import java.nio.file.Path;

/** What a session's own log says it spent. One CLI's log is not another's, so the format lives behind this. */
public interface SessionLog {

    /**
     * {@code upTo} is the byte the next read starts at, never mid-record, so a log appended to while it is read
     * loses no turn and counts none twice.
     */
    record Spent(TokenUsage usage, long upTo) {

        public static Spent nothing(long upTo) {
            return new Spent(TokenUsage.NONE, upTo);
        }
    }

    /** Reads at most {@code limit} bytes from {@code from}, so a log of any size costs a bounded read. */
    Spent spent(Path log, long from, long limit);
}

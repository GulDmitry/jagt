package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TokenUsage;

import java.nio.file.Path;

/**
 * What a session's own log says it spent. One CLI's log is not another's, so the format lives behind this: a
 * runtime whose log jagt cannot read answers nothing and costs a number, never a report.
 */
public interface SessionLog {

    /**
     * @param upTo the byte the next read starts at — never mid-record, so a log being appended to while it is
     *             read loses no turn and counts none twice
     */
    record Spent(TokenUsage usage, long upTo) {

        public static Spent nothing(long upTo) {
            return new Spent(TokenUsage.NONE, upTo);
        }
    }

    /**
     * @param from  where to start reading, which the caller remembers per log
     * @param limit how many bytes at most to read now, so a log of any size costs a bounded read
     */
    Spent spent(Path log, long from, long limit);
}

package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a task's own agent sessions have burned, read from the log each session keeps of itself.
 *
 * <p>A mark PER LOG is what keeps a re-read honest: a session appends, so only what is past its own mark is
 * new, and a task may have more than one log alive at once (a second session opened on a task whose first one
 * hung). One shared mark made those two re-read each other from nothing, over and over.
 *
 * <p>There is no cost here — a session's log prices nothing, and inventing a rate for it would be a guess in
 * dollars.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentSpend(TokenUsage usage, Map<String, Long> marks) {

    public static final AgentSpend NONE = new AgentSpend(TokenUsage.NONE, Map.of());

    public AgentSpend {
        // Insertion order is what says which log was heard from longest ago, and an immutable copy loses it.
        marks = marks == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(marks));
    }

    /** Where a read of that log starts: what was already counted, or nothing for a log never seen. */
    @JsonIgnore
    public long markFor(String log) {
        return marks.getOrDefault(log, 0L);
    }

    /** As many logs as a task plausibly has sessions; past that the oldest mark goes rather than the file grows. */
    private static final int MARKS_KEPT = 10;

    public AgentSpend plus(TokenUsage added, String log, long mark) {
        Map<String, Long> moved = new LinkedHashMap<>(marks);
        // Removed before it is put, so the order is oldest-first and what goes is the log heard from longest ago.
        moved.remove(log);
        moved.put(log, mark);
        while (moved.size() > MARKS_KEPT) {
            moved.remove(moved.keySet().iterator().next());
        }
        return new AgentSpend(usageOrNone().plus(added), moved);
    }

    @JsonIgnore
    public TokenUsage usageOrNone() {
        return usage == null ? TokenUsage.NONE : usage;
    }
}

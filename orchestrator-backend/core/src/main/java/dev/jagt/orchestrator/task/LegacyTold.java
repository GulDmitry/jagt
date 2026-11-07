package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Reads a state file written when the silence alert and the failed verification were marks on the turn. */
final class LegacyTold {

    private static final String SILENCE = "alerted:silence";
    private static final String VERIFY_FAILED = "verify:failed";
    private static final List<String> MARKS = List.of(SILENCE, VERIFY_FAILED);

    private LegacyTold() {
    }

    static @Nullable ToldOnce of(@Nullable ToldOnce stored, @Nullable Turn turn) {
        if (turn == null || MARKS.stream().noneMatch(turn.marks()::containsKey)) {
            return stored;
        }
        ToldOnce read = stored == null ? ToldOnce.none() : stored;
        String silence = turn.mark(SILENCE);
        if (silence != null && !read.has(ToldOnce.Kind.SILENCE)) {
            read = read.with(ToldOnce.Kind.SILENCE, silence);
        }
        String failed = turn.mark(VERIFY_FAILED);
        if (failed != null && !read.has(ToldOnce.Kind.VERIFY_FAILED)) {
            read = read.with(ToldOnce.Kind.VERIFY_FAILED, failed);
        }
        return read;
    }

    static @Nullable Turn withoutMarks(@Nullable Turn turn) {
        return turn == null ? null : turn.withoutMark(SILENCE).withoutMark(VERIFY_FAILED);
    }
}

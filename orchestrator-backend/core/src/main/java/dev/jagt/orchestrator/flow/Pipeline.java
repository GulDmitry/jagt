package dev.jagt.orchestrator.flow;

import java.util.Locale;

/**
 * What the code host last said about the checks, as a verdict rather than as its own wording: every host spells
 * this differently, and anything matching on the words would agree with the next reader only by luck.
 */
public enum Pipeline {

    /** Failed, errored, cancelled, timed out, or waiting on a human — nothing green will come of this run. */
    RED(0),
    /** Still going, or queued: the answer is not in yet. */
    RUNNING(1),
    /**
     * Nobody has read them: nothing looked yet, the read could not reach the host's pipelines, or the host said a
     * word this does not recognise — and the words hosts invent are not a list anyone can finish. Separate from
     * {@link #NONE} because "this request has no checks" is a fact the HOST stated, and answering ignorance with
     * it is how a red run leaves no mark on the board.
     */
    UNKNOWN(2),
    /** The host listed no pipeline for this request. Deliberately NOT green: nothing here says the code works. */
    NONE(3),
    GREEN(4);

    /** Worst first, for merging several repositories' rounds into one. */
    private final int severity;

    Pipeline(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public static Pipeline of(String hostStatus) {
        if (hostStatus == null || hostStatus.isBlank()) {
            return UNKNOWN;
        }
        String said = hostStatus.toLowerCase(Locale.ROOT);
        if (said.contains("fail") || said.contains("error") || said.contains("cancel")
                || said.contains("timed_out") || said.contains("timeout")
                || said.contains("action_required")) {
            return RED;
        }
        // `completed` is deliberately absent: on some hosts it says only that a run FINISHED, and the verdict is
        // reported separately — reading it as green would advance a task on a failed build.
        if (said.contains("success") || said.contains("passed")) {
            return GREEN;
        }
        if (said.contains("running") || said.contains("pending") || said.contains("queued")
                || said.contains("progress") || said.contains("created") || said.contains("waiting")) {
            return RUNNING;
        }
        // No host words it: an empty pipeline list is what "no checks" looks like on the wire, and the read that
        // saw that list is the only caller that can say so.
        if (said.contains("none")) {
            return NONE;
        }
        return UNKNOWN;
    }

    /** Whether a human should be told without being asked. */
    public boolean worthATap() {
        return this == RED;
    }
}

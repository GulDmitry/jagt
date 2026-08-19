package dev.jagt.orchestrator.flow;

import java.util.Locale;

/**
 * What the code host last said about the checks, as a verdict rather than as its own wording: every host spells
 * this differently (`success`, `SUCCESS`, `passed`, `running`, `pending`, `failed`, `error`, `timed_out`), and a
 * surface that matched on the words would agree with the other surface only by luck — and with the sweep's own
 * decision only by luck too, which is the disagreement that matters.
 *
 * <p>One parser, because the console's marker and the board's dot are the same fact seen twice.
 */
public enum Pipeline {

    /** Failed, errored, cancelled, timed out, or waiting on a human — nothing green will come of this run. */
    RED(0),
    /** Still going, or queued: the answer is not in yet. */
    RUNNING(1),
    /**
     * No checks, or a word this does not recognise. Deliberately NOT green: a verdict nobody can read must never
     * be what advances a task, and the words hosts invent are not a list anyone can finish.
     */
    NONE(2),
    /** The checks passed. */
    GREEN(3);

    /** Worst first, for merging several repositories' rounds into the one a human is owed. */
    private final int severity;

    Pipeline(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public static Pipeline of(String hostStatus) {
        if (hostStatus == null || hostStatus.isBlank()) {
            return NONE;
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
        return NONE;
    }

    /** Whether a human should be told without being asked: a run that went red is the one that cannot wait. */
    public boolean worthATap() {
        return this == RED;
    }
}

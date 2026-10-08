package dev.jagt.orchestrator.task;

import java.util.Locale;

/** Who asked for a status change. */
public enum ActionOrigin {

    BOARD,
    /** Free text a model mapped to a command, whichever surface it was typed into. */
    PALETTE,
    /** An MCP call: a sub-agent reporting its own progress, or the Master. */
    MCP,
    AUTO_REVIEW,
    /** The Master session acting where a human would have. */
    MASTER,
    /** A stage the tracker itself reports, which is nobody's judgement and so nobody's to hold. */
    TRACKER;

    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

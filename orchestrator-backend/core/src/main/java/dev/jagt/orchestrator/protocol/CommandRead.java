package dev.jagt.orchestrator.protocol;

import java.util.Collection;

/**
 * One line of free text mapped onto one command of the grammar — a proposal, never an execution. The answer is
 * CONSTRAINED to the commands and tasks the board offers: a word off the list cannot be decoded.
 */
public final class CommandRead {

    public static final String NONE = "none";

    private CommandRead() {
    }

    /** {@code task} may also be empty: {@code do} and {@code resume} act on no task. */
    public static Schema schemaFor(Collection<String> commands, Collection<String> tasks) {
        return Schema.answer()
                .choiceRequired("command", Wire.withNone(commands, NONE), null)
                .choiceRequired("task", Wire.withNone(tasks, ""), null)
                .required("ticket", "string", null)
                .required("reason", "string", null);
    }
}

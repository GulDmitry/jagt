package dev.jagt.orchestrator.protocol;

/** One line of free text mapped onto one command of the grammar — a proposal, never an execution. */
public final class CommandRead {

    public static final Schema SCHEMA = Schema.answer()
            .required("command", "string", null)
            .required("task", "string", null)
            .required("ticket", "string", null)
            .required("reason", "string", null);

    private CommandRead() {
    }
}

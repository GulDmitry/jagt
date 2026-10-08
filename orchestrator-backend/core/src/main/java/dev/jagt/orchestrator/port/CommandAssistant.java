package dev.jagt.orchestrator.port;

/** Free text mapped onto ONE command of the console grammar — a PROPOSAL, never an execution. Every call is booked. */
public interface CommandAssistant {

    /** {@code command} empty (or "none") means nothing matched, and {@code reason} then says why in one line. */
    record CommandProposal(String command, String task, String ticket, String reason) {
    }

    /**
     * {@code context} is the prompt-ready list of commands and current tasks to consider. Reads NOTHING from the
     * outside, so an implementation runs with no tools at all.
     */
    Answer<CommandProposal> mapCommand(String text, String context);
}

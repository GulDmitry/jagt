package dev.jagt.orchestrator.protocol;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Anything that crosses into jagt from outside. Judged before it is acted on, never after, and judged in ONE
 * place so that no caller can be the one that forgot.
 */
public interface Message {

    /** Every rule broken, field and consistency together; empty means the message can be acted on. */
    List<Violation> violations(MessageContext context);

    /**
     * What goes back to whoever sent it. A refusal is a correction — the sender's next move is the same message
     * with every line fixed — so it names each field and what was expected there.
     */
    static String refusal(List<Violation> violations) {
        return "This message was not accepted. Fix every line and send it again:\n"
                + violations.stream().map(Violation::toString).collect(Collectors.joining("\n"));
    }
}

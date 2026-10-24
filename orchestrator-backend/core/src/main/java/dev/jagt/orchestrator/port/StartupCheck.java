package dev.jagt.orchestrator.port;

import java.util.List;

/**
 * One part of the installation, asked before jagt serves anything whether it has what it needs. Implemented next to
 * the part it answers for, so nothing branches on which terminal, agent or host is configured; a configured type
 * selecting no implementation is checked where the parts are assembled.
 *
 * <p>The question is presence, never health: nothing reaches the network.
 */
public interface StartupCheck {

    /** Everything this part needs and does not have. Empty = ready. */
    List<Problem> problems();

    /**
     * Whether it asks about the machine jagt runs on, which a test runner is not, so switching the checks off
     * skips it. A check of jagt's own files answers false and runs always.
     */
    default boolean machine() {
        return true;
    }
}

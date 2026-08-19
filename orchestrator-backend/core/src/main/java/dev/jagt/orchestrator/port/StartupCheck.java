package dev.jagt.orchestrator.port;

import java.util.List;

/**
 * One part of the installation, asked before jagt serves anything whether it has what it needs.
 *
 * <p>Implemented NEXT TO the part it answers for, so a check exists only when that implementation was selected
 * and nothing has to branch on which terminal, agent or host is configured. A configured type that selects no
 * implementation at all has nobody to answer for it, and is checked where the parts are assembled.
 *
 * <p>The question is presence, never health: nothing reaches the network. A token jagt has not tried yet is a
 * different fact from a token nobody set, and a laptop offline would refuse to start.
 */
public interface StartupCheck {

    /**
     * Everything this part needs and does not have — one self-contained sentence each, naming the key that
     * fixes it. Empty = ready.
     */
    List<String> problems();
}

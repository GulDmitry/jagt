package dev.jagt.orchestrator.port;

import java.util.List;

/**
 * One part of the installation, asked before jagt serves anything whether it has what it needs.
 *
 * <p>Implemented NEXT TO the part it answers for — a seam's own implementation checks its own binaries and
 * files, so the check bean exists only when that implementation was selected and nothing has to branch on
 * which terminal, agent or host is configured. What a part cannot answer for itself (a configured type that
 * selects no implementation at all) is checked here instead.
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

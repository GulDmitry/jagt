package dev.jagt.orchestrator.command;

import java.util.List;

/**
 * A command no task owns: it reports jagt's own state or starts something, so no {@code Move} can gate it and no
 * task card can offer it. Every surface reads this declaration instead of naming the verb itself, which is what
 * makes a new capability one class rather than an edit per front-end.
 */
public interface GlobalCommand {

    /** The typed verb and the wire id, one string so a console line and a request cannot drift apart. */
    String id();

    /** One line, for a human choosing between verbs. */
    String hint();

    /** What a human types. Lines after the first are modifiers, shown under it as they are given. */
    default List<String> usage() {
        return List.of(id());
    }

    /** Whether the answer is a text REPORT to open, rather than a sentence to log. */
    default boolean report() {
        return false;
    }

    /**
     * Whether the answer is about ONE task. A surface with cards puts it on the card that has something to show
     * and offers no bar button beside it: pressed with nothing named, such a report answers for every task at
     * once, which is a list a human has to read past to reach the one they meant.
     */
    default boolean aboutOneTask() {
        return false;
    }

    /** Runs it. {@code tail} is what was typed after the verb, blank when nothing was; each command parses its own. */
    String run(String tail);
}

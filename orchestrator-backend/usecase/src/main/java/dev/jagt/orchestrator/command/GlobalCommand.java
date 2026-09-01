package dev.jagt.orchestrator.command;

import java.util.List;

/** A command no task owns. Every surface reads this declaration instead of naming the verb itself. */
public interface GlobalCommand {

    /** The typed verb and the wire id, one string so a console line and a request cannot drift apart. */
    String id();

    /** One line. */
    String hint();

    /** What a human types. Lines after the first are modifiers, shown under it as they are given. */
    default List<String> usage() {
        return List.of(id());
    }

    /** A text report to open, rather than a sentence to log. */
    default boolean report() {
        return false;
    }

    /** The answer is about ONE task, so a surface with cards puts it on the card rather than in the bar. */
    default boolean aboutOneTask() {
        return false;
    }

    /** {@code tail} is what was typed after the verb, blank when nothing was. */
    String run(String tail);
}

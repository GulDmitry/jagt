package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.TaskView;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A command no task owns. Every surface reads this declaration instead of naming the verb itself.
 */
public interface GlobalCommand {

  /**
   * The typed verb and the wire id, one string so a console line and a request cannot drift apart.
   */
  String id();

  /** One line. */
  String hint();

  /** Where the verb sorts among every verb, task actions included: most-used lowest. */
  int rank();

  /** What a human types. Lines after the first are modifiers, shown under it as they are given. */
  default List<String> usage() {
    return List.of(id());
  }

  /** A text report to open, rather than a sentence to log. */
  default boolean report() {
    return false;
  }

  /**
   * The answer is about ONE task, so a surface with cards puts it on the card rather than in the
   * bar.
   */
  default boolean aboutOneTask() {
    return false;
  }

  /** A report the card of {@code task} offers, so no surface keeps a list of when to show it. */
  default boolean offeredOn(TaskView task) {
    return false;
  }

  /**
   * The part of a surface the verb belongs to, or null: what typed alone hands over to, or the tab
   * a report feeds.
   */
  default @Nullable String part() {
    return null;
  }

  /** Why the Master never runs this command; empty where it may. */
  default Optional<String> whyNotTheMaster() {
    return Optional.empty();
  }

  /** {@code tail} is what was typed after the verb, blank when nothing was. */
  String run(String tail);
}

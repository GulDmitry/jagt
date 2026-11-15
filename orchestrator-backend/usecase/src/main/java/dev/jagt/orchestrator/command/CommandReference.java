package dev.jagt.orchestrator.command;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The command grammar, rendered from the declarations themselves; every surface renders THIS one.
 */
public final class CommandReference {

  /** What only a verb a task owns declares. */
  public record TaskOnly(List<String> aliases, boolean takesProject) {}

  public record Verb(
      String id,
      String hint,
      boolean takesTask,
      @JsonUnwrapped TaskOnly taskOnly,
      boolean report,
      boolean aboutOneTask,
      @Nullable String part,
      @Nullable String confirmation) {

    /** Built from the declaration rather than by a caller: adjacent flags transpose in silence. */
    static Verb of(TaskAction action) {
      return new Verb(
          action.id(),
          action.hint(),
          true,
          new TaskOnly(action.retiredVerbs(), action.takesProject()),
          false,
          false,
          null,
          null);
    }

    static Verb of(GlobalCommand command) {
      return new Verb(
          command.id(),
          command.hint(),
          false,
          new TaskOnly(List.of(), false),
          command.report(),
          command.aboutOneTask(),
          command.part(),
          command.confirmation().orElse(null));
    }
  }

  private record Declared(int rank, Verb verb, List<String> usage) {}

  private static final int HINT_COLUMN = 29;

  private CommandReference() {}

  public static List<Verb> verbs(Collection<GlobalCommand> globals) {
    return declared(globals).stream().map(Declared::verb).toList();
  }

  public static String text(Collection<GlobalCommand> globals) {
    List<String> lines = new ArrayList<>();
    lines.add("commands (task = ticket id or alias):");
    for (Declared declared : declared(globals)) {
      lines.add(row(declared.usage().get(0), declared.verb().hint()));
      declared.usage().stream().skip(1).forEach(modifier -> lines.add("  " + modifier));
    }
    lines.add("");
    lines.add("Ask / ⌘K takes free text: a model maps it to one verb above, gated like a button.");
    return String.join("\n", lines);
  }

  private static List<Declared> declared(Collection<GlobalCommand> globals) {
    List<Declared> declared = new ArrayList<>();
    for (TaskAction action : TaskAction.values()) {
      declared.add(new Declared(action.rank(), Verb.of(action), List.of(action.usage())));
    }
    for (GlobalCommand command : globals) {
      declared.add(new Declared(command.rank(), Verb.of(command), command.usage()));
    }
    // Rank, then the id: two equal ranks would come out in container order.
    declared.sort(
        Comparator.comparingInt(Declared::rank).thenComparing(entry -> entry.verb().id()));
    return List.copyOf(declared);
  }

  private static String row(String usage, String hint) {
    String gap = usage.length() < HINT_COLUMN ? " ".repeat(HINT_COLUMN - usage.length()) : " ";
    return "  " + usage + gap + hint;
  }
}

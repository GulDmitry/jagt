package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.TaskAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The command grammar, rendered from the declarations themselves — {@link TaskAction} for what a task owns and
 * {@link GlobalCommand} for what it does not. Every surface renders THIS, so a verb cannot exist in one and be
 * missing from the next, and no hint is written twice.
 */
public final class CommandReference {

    public record Verb(String id, String hint, boolean takesTask, List<String> aliases, boolean report,
                       boolean consoleOnly) {
    }

    private record Declared(Verb verb, List<String> usage) {
    }

    /** Most-used first; a verb missing here sorts to the end rather than being dropped. */
    private static final List<String> BY_USE = List.of(
            "sweep", "ship", "do", "ide", "diff", "focus", "resume", "deploy", "stats", "respawn",
            "revert", "done", "activity", "jobs", "status", "help");

    private static final int HINT_COLUMN = 29;

    private CommandReference() {
    }

    /** Every verb, console and board alike; a caller that is not a terminal filters {@link Verb#consoleOnly}. */
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
        // Stopping the backend belongs to whoever owns the process, so it is the one verb no other surface has.
        lines.add(row("quit", "detach: the shell exits, the agents keep running"));
        lines.add("");
        lines.add("anything else is free text: a model maps it to ONE of the above and jagt runs it through the");
        lines.add("same gate a button uses (the board's Ask / ⌘K).");
        return String.join("\n", lines);
    }

    private static List<Declared> declared(Collection<GlobalCommand> globals) {
        List<Declared> declared = new ArrayList<>();
        for (TaskAction action : TaskAction.values()) {
            declared.add(new Declared(new Verb(action.id(), action.hint(), true, action.retiredVerbs(), false,
                    false), List.of(action.usage())));
        }
        for (GlobalCommand command : globals) {
            declared.add(new Declared(new Verb(command.id(), command.hint(), false, List.of(), command.report(),
                    command.consoleOnly()), command.usage()));
        }
        // Rank, then the id: two commands the order does not name would otherwise come out in whatever
        // order the container handed them over.
        declared.sort(Comparator.<Declared>comparingInt(entry -> rankOf(entry.verb().id()))
                .thenComparing(entry -> entry.verb().id()));
        return List.copyOf(declared);
    }

    private static String row(String usage, String hint) {
        String gap = usage.length() < HINT_COLUMN ? " ".repeat(HINT_COLUMN - usage.length()) : " ";
        return "  " + usage + gap + hint;
    }

    private static int rankOf(String id) {
        int rank = BY_USE.indexOf(id);
        return rank < 0 ? BY_USE.size() : rank;
    }
}

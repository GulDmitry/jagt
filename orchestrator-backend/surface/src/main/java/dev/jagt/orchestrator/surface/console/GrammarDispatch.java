package dev.jagt.orchestrator.surface.console;

import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.command.CommandReference;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.OriginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tier 1 of the dispatch: a typed line, parsed by a fixed grammar and executed in-process. No model, no MCP
 * round-trip. Free text falls through to {@link NaturalLanguageDispatch}, which is the only path that spends
 * tokens.
 *
 * <p>It KNOWS no verb: the first word is looked up among the commands a task does not own ({@link
 * GlobalCommands}) and then among the ones it does ({@link TaskAction}), so a new capability is declared once
 * and typed here without an edit.
 */
@Component
@RequiredArgsConstructor
public class GrammarDispatch {

    private final CommandService commands;
    private final GlobalCommands globals;
    private final NaturalLanguageDispatch naturalLanguage;

    /** A blank answer means the caller shows the dashboard alone. */
    public String run(String line) {
        return OriginContext.as(ActionOrigin.CONSOLE, () -> runHere(line));
    }

    /**
     * Every spelling the console completes on, so the shell keeps no second list of verbs. Retired ones are in
     * it BECAUSE `rev` must stay ambiguous — completing that prefix to `revert` alone would fill in a
     * shared-branch write for someone whose fingers meant the sweep.
     */
    public List<String> completions() {
        return CommandReference.verbs(globals.all()).stream()
                .flatMap(verb -> Stream.concat(Stream.of(verb.id()), verb.aliases().stream()))
                .distinct().sorted().toList();
    }

    private String runHere(String line) {
        List<String> tok = List.of(line.strip().split("\\s+"));
        String verb = tok.get(0).toLowerCase(Locale.ROOT);
        try {
            Optional<GlobalCommand> global = globals.byId(verb);
            if (global.isPresent()) {
                return global.get().run(tail(line));
            }
            // A retired spelling resolves here so muscle memory costs no model call, and nowhere is it offered.
            Optional<TaskAction> action = TaskAction.byId(verb).or(() -> TaskAction.byRetiredVerb(verb));
            return action.isPresent() ? act(tok, action.get()) : naturalLanguage.interpret(line);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "error: " + e.getMessage();
        }
    }

    /** The same action a board button posts, through the gate that refuses what a status does not allow. */
    private String act(List<String> tok, TaskAction action) {
        TaskAction wanted = action == TaskAction.IDE
                && tok.stream().anyMatch(token -> token.equalsIgnoreCase("diff")) ? TaskAction.DIFF : action;
        if (tok.size() <= 1 || tok.get(1).isBlank()) {
            throw new IllegalArgumentException("usage: " + wanted.usage());
        }
        return commands.execute(tok.get(1), wanted);
    }

    private static String tail(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}

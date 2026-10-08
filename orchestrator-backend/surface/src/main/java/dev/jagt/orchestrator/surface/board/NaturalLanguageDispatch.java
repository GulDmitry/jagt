package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.port.CommandAssistant.CommandProposal;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.flow.TaskAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What happens when the grammar does NOT match: a model maps free text onto one grammar command, and deterministic
 * code validates and executes it. The model only ever PROPOSES — it holds no git, no state and no gate, and the
 * call carries no MCP servers and no tools. What it understood is reported BEFORE the outcome, so it can be
 * corrected.
 */
@Service
@RequiredArgsConstructor
public class NaturalLanguageDispatch {

    /** Mapping a retired verb onto a live command is the one guess that could be destructive. */
    private static final Map<String, String> RETIRED = Map.of(
            "prune", "jagt has no `prune`: cleaning up a merged branch is that one task's business, and yours"
                    + " to do with git.");

    private final CommandAssistant assistant;
    private final PaletteContext board;
    private final CommandService commands;
    private final TaskLauncher launcher;

    /**
     * Interprets free text and runs what it means. Never throws for a request it cannot place; a refusal from the
     * gate below does propagate, that being a real answer about a real task.
     */
    public String interpret(String text) {
        return OriginContext.as(ActionOrigin.PALETTE, () -> interpretHere(text));
    }

    private String interpretHere(String text) {
        if (text == null || text.isBlank()) {
            return "Nothing to interpret.";
        }
        String retired = RETIRED.get(text.strip().split("\\s+")[0].toLowerCase(Locale.ROOT));
        if (retired != null) {
            return retired;
        }
        // A single word that is not a command is a typo, and it cannot name both an action and a task anyway.
        if (text.strip().split("\\s+").length == 1) {
            return "Unknown command '" + text.strip() + "' — type `help` for the grammar, or say what you"
                    + " want in a few words.";
        }
        Optional<CommandProposal> proposal = assistant.mapCommand(text, board.prompt()).facts();
        if (proposal.isEmpty()) {
            return "Could not reach the assistant to interpret \"" + text.strip() + "\" — type `help` for the"
                    + " command grammar.";
        }
        CommandProposal mapped = proposal.get();
        String command = mapped.command() == null ? "" : mapped.command().strip().toLowerCase(Locale.ROOT);
        if (command.isBlank() || command.equals("none")) {
            return "Not clear enough to act on: " + reasonOf(mapped) + " Type `help` for the grammar.";
        }
        if (command.equals(PaletteContext.DO)) {
            return runDo(mapped);
        }
        if (command.equals(PaletteContext.RESUME)) {
            return runResume(mapped);
        }
        Optional<TaskAction> action = TaskAction.byId(command).or(() -> TaskAction.byRetiredVerb(command));
        if (action.isEmpty()) {
            return "Mapped \"" + text.strip() + "\" to an unknown command '" + command + "' — refused."
                    + " Type `help` for the grammar.";
        }
        return run(action.get(), mapped);
    }

    private String run(TaskAction action, CommandProposal mapped) {
        // Echoed as the action's OWN id: the model may have proposed a spelling the grammar was renamed from.
        String verb = action.id();
        String task = board.existingTask(mapped.task());
        if (task == null) {
            return "Understood as `" + verb + "` but not which task (" + reasonOf(mapped)
                    + ") — name it: `" + verb + " <ticket|alias>`.";
        }
        String understood = "understood as `" + verb + " " + task + "` — ";
        try {
            return understood + commands.execute(task, action);
        } catch (Refusal e) {
            // Rethrown rather than returned: a refusal answered as text reads as a success to every caller.
            throw new Refusal(e.code(), understood + "refused: " + e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // A refusal naming a task the operator never typed explains nothing without the interpretation.
            throw new IllegalStateException(understood + "refused: " + e.getMessage(), e);
        }
    }

    private String runDo(CommandProposal mapped) {
        String ticket = mapped.ticket() == null ? "" : mapped.ticket().strip();
        if (ticket.isBlank()) {
            return "Understood as `do` but no ticket was named (" + reasonOf(mapped)
                    + ") — say it explicitly: `do <ticket|url> [project]`.";
        }
        return "understood as `do " + ticket + "` — " + launcher.launch(LaunchRequest.of(ticket))
                .message();
    }

    /** The URL is carried in the same field a ticket would be, and it must BE a URL. */
    private String runResume(CommandProposal mapped) {
        String url = mapped.ticket() == null ? "" : mapped.ticket().strip();
        if (!url.startsWith("http")) {
            return "Understood as `resume` but no review-request URL was named (" + reasonOf(mapped)
                    + ") — say it explicitly: `resume <request-url>`.";
        }
        return "understood as `resume " + url + "` — " + launcher.resume(url).message();
    }

    private static String reasonOf(CommandProposal mapped) {
        return mapped.reason() == null || mapped.reason().isBlank() ? "no reason given" : mapped.reason().strip();
    }
}

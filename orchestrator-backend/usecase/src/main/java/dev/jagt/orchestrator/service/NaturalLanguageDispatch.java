package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.port.MasterAssistant.CommandProposal;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What happens when the grammar does NOT match: a model maps free text onto one grammar command, and deterministic
 * code validates and executes it. The model only ever PROPOSES — it holds no git, no state and no gate, and the
 * call carries no MCP servers and no tools. What it understood is reported BEFORE the outcome, so it can be
 * corrected.
 */
@Service
@RequiredArgsConstructor
public class NaturalLanguageDispatch {

    /** Not a TaskAction: `do` creates a task rather than acting on one, and it is the commonest request. */
    private static final String DO = "do";
    /** Also not a TaskAction: `resume` takes over an EXISTING review request instead of starting anything. */
    private static final String RESUME = "resume";

    /** Mapping a retired verb onto a live command is the one guess that could be destructive. */
    private static final Map<String, String> RETIRED = Map.of(
            "prune", "jagt has no `prune`: cleaning up a merged branch is that one task's business, and yours"
                    + " to do with git.");

    private final MeteredAssistant assistant;
    private final StateService stateService;
    private final TaskViews taskViews;
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
        String retired = RETIRED.get(text.strip().split("\\s+")[0].toLowerCase(java.util.Locale.ROOT));
        if (retired != null) {
            return retired;
        }
        // A single word that is not a command is a typo, and it cannot name both an action and a task anyway.
        if (text.strip().split("\\s+").length == 1) {
            return "Unknown command '" + text.strip() + "' — type `help` for the grammar, or say what you"
                    + " want in a few words.";
        }
        Optional<CommandProposal> proposal = assistant.mapCommand(text, context()).facts();
        if (proposal.isEmpty()) {
            return "Could not reach the assistant to interpret \"" + text.strip() + "\" — type `help` for the"
                    + " command grammar.";
        }
        CommandProposal mapped = proposal.get();
        String command = mapped.command() == null ? "" : mapped.command().strip().toLowerCase(java.util.Locale.ROOT);
        if (command.isBlank() || command.equals("none")) {
            return "Not clear enough to act on: " + reasonOf(mapped) + " Type `help` for the grammar.";
        }
        if (command.equals(DO)) {
            return runDo(mapped);
        }
        if (command.equals(RESUME)) {
            return runResume(mapped);
        }
        Optional<TaskAction> action = TaskAction.byId(command).or(() -> TaskAction.byRetiredVerb(command));
        if (action.isEmpty()) {
            return "Mapped \"" + text.strip() + "\" to an unknown command '" + command + "' — refused."
                    + " Type `help` for the grammar.";
        }
        // Echoed as the action's OWN id: the model may have proposed a spelling the grammar was renamed from.
        String verb = action.get().id();
        String task = resolveTask(mapped.task());
        if (task == null) {
            return "Understood as `" + verb + "` but not which task (" + reasonOf(mapped)
                    + ") — name it: `" + verb + " <ticket|alias>`.";
        }
        String understood = "understood as `" + verb + " " + task + "` — ";
        try {
            return understood + commands.execute(task, action.get());
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

    /** Only a task that EXISTS may be acted on; an id the model invented resolves to nothing and is refused. */
    private String resolveTask(String proposed) {
        if (proposed == null || proposed.isBlank()) {
            return null;
        }
        String canonical = stateService.canonicalTaskId(proposed.strip());
        return stateService.task(canonical).isPresent() ? canonical : null;
    }

    private static String reasonOf(CommandProposal mapped) {
        return mapped.reason() == null || mapped.reason().isBlank() ? "no reason given" : mapped.reason().strip();
    }

    /** Built from the SAME projection the surfaces render, so nothing off the board can be proposed. */
    public String context() {
        String commandList = java.util.Arrays.stream(TaskAction.values())
                .map(action -> "- " + action.id() + ": " + action.hint())
                .collect(Collectors.joining("\n"));
        Map<String, TaskState> tasks = stateService.tasks();
        String taskList = taskViews.all().stream()
                .map(NaturalLanguageDispatch::taskLine)
                .collect(Collectors.joining("\n"));
        return "COMMANDS (one of these words, or \"none\"):\n" + commandList
                + "\n- " + DO + ": start a NEW task from a ticket key or URL (the `ticket` field carries it)"
                + "\n- " + RESUME + ": take over an EXISTING review request / merge request — its branch and its"
                + " commits — when the human gives such a URL (the `ticket` field carries the URL)\n\n"
                + "TASKS currently registered"
                + (tasks.isEmpty() ? " — NONE, so only `do` or `none` can apply:\n(none)"
                        : " (use the id or alias verbatim):\n" + taskList);
    }

    /** One task per line, including what is legal for it — the model should not propose a refused action. */
    private static String taskLine(TaskView task) {
        String legal = task.actions().stream().map(TaskView.ActionView::id).collect(Collectors.joining(","));
        return "- id=" + task.id() + " alias=" + (task.alias() == null ? "-" : task.alias())
                + " status=" + task.status() + " title=\"" + (task.title() == null ? "" : task.title())
                + "\" legal=" + legal;
    }
}

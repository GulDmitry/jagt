package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.assistant.MasterAssistant.CommandProposal;
import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskView;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tier 2 of the two-tier dispatch: what happens when the grammar does NOT match. A model maps the free text
 * onto one grammar command, and then deterministic code — the same {@link CommandService} a button and a typed
 * command go through — validates and executes it. The model only ever PROPOSES: it holds no git, no state, no
 * gate, so a wrong guess costs a sentence, not a push (the asymmetric-failure rule).
 *
 * <p>Tier 1 stays on the hot path for ~every interaction, so tokens and latency are spent ONLY when someone
 * actually uses the flexibility. The call itself is the cheapest jagt makes: no MCP servers, no tools, and a
 * context that is one list of commands plus one list of tasks.
 *
 * <p>It always reports what it understood BEFORE the outcome ("understood as `ship a2` — …"). A dispatcher
 * whose interpretation is invisible teaches nobody the grammar and cannot be corrected.
 */
@Service
public class NaturalLanguageDispatch {

    /** Not a TaskAction: `do` creates a task rather than acting on one, and it is the commonest request. */
    private static final String DO = "do";

    private final MeteredAssistant assistant;
    private final StateService stateService;
    private final TaskViews taskViews;
    private final CommandService commands;
    private final TaskLauncher launcher;

    public NaturalLanguageDispatch(MeteredAssistant assistant, StateService stateService, TaskViews taskViews,
                                   CommandService commands, TaskLauncher launcher) {
        this.assistant = assistant;
        this.stateService = stateService;
        this.taskViews = taskViews;
        this.commands = commands;
        this.launcher = launcher;
    }

    /**
     * Interprets free text and runs what it means. Never throws for a request it cannot place — an operator
     * typing prose gets an explanation and the grammar, not a stack trace. A refusal from the gate below
     * (wrong status, unknown project) does propagate: that is a real answer about a real task.
     */
    public String interpret(String text) {
        if (text == null || text.isBlank()) {
            return "Nothing to interpret.";
        }
        // A single word reached tier 2 because it is not a command — overwhelmingly a typo (`shipp`), and a
        // typo must not cost a model call. It also cannot name both an action and a task, so there is nothing
        // for the mapper to do with it.
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
        String command = mapped.command() == null ? "" : mapped.command().strip().toLowerCase();
        if (command.isBlank() || command.equals("none")) {
            return "Not clear enough to act on: " + reasonOf(mapped) + " Type `help` for the grammar.";
        }
        if (command.equals(DO)) {
            return runDo(mapped);
        }
        Optional<TaskAction> action = TaskAction.byId(command);
        if (action.isEmpty()) {
            return "Mapped \"" + text.strip() + "\" to an unknown command '" + command + "' — refused."
                    + " Type `help` for the grammar.";
        }
        String task = resolveTask(mapped.task());
        if (task == null) {
            return "Understood as `" + command + "` but not which task (" + reasonOf(mapped)
                    + ") — name it: `" + command + " <ticket|alias>`.";
        }
        // Execution goes through the SAME gate as a button: an action the task's status does not allow is
        // refused with a sentence here, so a model's guess can never widen what is legal.
        return "understood as `" + command + " " + task + "` — " + commands.execute(task, action.get());
    }

    private String runDo(CommandProposal mapped) {
        String ticket = mapped.ticket() == null ? "" : mapped.ticket().strip();
        if (ticket.isBlank()) {
            return "Understood as `do` but no ticket was named (" + reasonOf(mapped)
                    + ") — say it explicitly: `do <ticket|url> [project]`.";
        }
        return "understood as `do " + ticket + "` — " + launcher.launch(LaunchRequest.of(ticket));
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

    /**
     * The prompt's whole world: every command with what it does, and every task with the facts a human would
     * use to pick one (alias, id, status, title). Built from the SAME projection the surfaces render, so the
     * mapper cannot be offered a command or a task that the board does not show.
     */
    private String context() {
        String commandList = java.util.Arrays.stream(TaskAction.values())
                .map(action -> "- " + action.id() + ": " + action.hint())
                .collect(Collectors.joining("\n"));
        Map<String, TaskState> tasks = stateService.tasks();
        String taskList = taskViews.all().stream()
                .map(NaturalLanguageDispatch::taskLine)
                .collect(Collectors.joining("\n"));
        return "COMMANDS (one of these words, or \"none\"):\n" + commandList
                + "\n- " + DO + ": start a NEW task from a ticket key or URL (the `ticket` field carries it)\n\n"
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

package dev.jagt.orchestrator.surface.board.task;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.assistant.CommandAssistant;
import dev.jagt.orchestrator.port.assistant.CommandAssistant.CommandProposal;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What happens when the grammar does NOT match: a model maps free text onto one grammar command,
 * and deterministic code validates and executes it. The model only ever PROPOSES — it holds no git,
 * no state and no gate, and the call carries no MCP servers and no tools. What it understood is
 * reported BEFORE the outcome, so it can be corrected.
 */
@Service
@RequiredArgsConstructor
public class NaturalLanguageDispatch {

  private final CommandAssistant assistant;
  private final PaletteContext palette;
  private final CommandService commands;
  private final GlobalCommands globals;

  /**
   * Interprets free text and runs what it means. Never throws for a request it cannot place; a
   * refusal from the gate below does propagate, that being a real answer about a real task.
   */
  public String interpret(String text) {
    return OriginContext.as(ActionOrigin.PALETTE, () -> interpretHere(text));
  }

  private String interpretHere(String text) {
    if (text == null || text.isBlank()) {
      return "Nothing to interpret.";
    }
    Optional<String> retired = TaskAction.whyRetired(text.strip().split("\\s+", 2)[0]);
    if (retired.isPresent()) {
      return retired.get();
    }
    // A single word that is not a command is a typo, and it cannot name both an action and a task
    // anyway.
    if (text.strip().split("\\s+").length == 1) {
      return "Unknown command '"
          + text.strip()
          + "' — type `help` for the grammar, or say what you"
          + " want in a few words.";
    }
    Optional<CommandProposal> proposal = assistant.mapCommand(text, palette.menu()).facts();
    if (proposal.isEmpty()) {
      return "Could not reach the assistant to interpret \""
          + text.strip()
          + "\" — type `help` for the"
          + " command grammar.";
    }
    CommandProposal mapped = proposal.get();
    String command =
        mapped.command() == null ? "" : mapped.command().strip().toLowerCase(Locale.ROOT);
    if (command.isBlank() || command.equals("none")) {
      return "Not clear enough to act on: " + reasonOf(mapped) + " Type `help` for the grammar.";
    }
    Optional<GlobalCommand> proposed =
        globals.byId(command).filter(global -> global.modelHint().isPresent());
    if (proposed.isPresent()) {
      String target = mapped.ticket() == null ? "" : mapped.ticket().strip();
      return proposed.get().runProposed(target, reasonOf(mapped));
    }
    Optional<TaskAction> action =
        TaskAction.byId(command).or(() -> TaskAction.byRetiredVerb(command));
    if (action.isEmpty()) {
      return "Mapped \""
          + text.strip()
          + "\" to an unknown command '"
          + command
          + "' — refused."
          + " Type `help` for the grammar.";
    }
    return run(action.get(), mapped);
  }

  private String run(TaskAction action, CommandProposal mapped) {
    // Echoed as the action's OWN id: the model may have proposed a spelling the grammar was renamed
    // from.
    String verb = action.id();
    String task = palette.existingTask(mapped.task());
    if (task == null) {
      return "Understood as `"
          + verb
          + "` but not which task ("
          + reasonOf(mapped)
          + ") — name it: `"
          + verb
          + " <ticket|alias>`.";
    }
    String understood = "understood as `" + verb + " " + task + "` — ";
    try {
      return understood + commands.execute(task, action);
    } catch (Refusal e) {
      // Rethrown rather than returned: a refusal answered as text reads as a success to every
      // caller.
      throw new Refusal(e.code(), understood + "refused: " + e.getMessage());
    } catch (IllegalArgumentException | IllegalStateException e) {
      // A refusal naming a task the operator never typed explains nothing without the
      // interpretation.
      throw new IllegalStateException(understood + "refused: " + e.getMessage(), e);
    }
  }

  private static String reasonOf(CommandProposal mapped) {
    return mapped.reason() == null || mapped.reason().isBlank()
        ? "no reason given"
        : mapped.reason().strip();
  }
}

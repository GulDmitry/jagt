package dev.jagt.orchestrator.surface.board.task;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.TaskView;
import dev.jagt.orchestrator.port.assistant.CommandAssistant;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.task.TaskViews;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The board as the palette's model sees it, and the one way back from a task it named to a task
 * that exists.
 */
@Component
@RequiredArgsConstructor
public class PaletteContext {

  private final StateService stateService;
  private final TaskViews taskViews;
  private final GlobalCommands globals;

  /**
   * Built from the SAME projection the surfaces render, so nothing off the board can be proposed.
   */
  public CommandAssistant.Menu menu() {
    List<TaskView> views = taskViews.all();
    List<String> commands =
        Stream.concat(
                Arrays.stream(TaskAction.values()).map(TaskAction::id),
                proposable().stream().map(GlobalCommand::id))
            .toList();
    List<String> tasks =
        views.stream()
            .flatMap(task -> Stream.of(task.id(), task.alias()))
            .filter(name -> name != null && !name.isBlank())
            .toList();
    return new CommandAssistant.Menu(prompt(views), commands, tasks);
  }

  private List<GlobalCommand> proposable() {
    return globals.all().stream().filter(global -> global.modelHint().isPresent()).toList();
  }

  private String prompt(List<TaskView> views) {
    String commandList =
        Arrays.stream(TaskAction.values())
            .map(action -> "- " + action.id() + ": " + action.hint())
            .collect(Collectors.joining("\n"));
    String taskList =
        views.stream().map(PaletteContext::taskLine).collect(Collectors.joining("\n"));
    return "COMMANDS (one of these words, or \"none\"):\n"
        + commandList
        + proposable().stream()
            .map(global -> "\n- " + global.id() + ": " + global.modelHint().orElseThrow())
            .collect(Collectors.joining())
        + "\n\nTASKS currently registered"
        + (stateService.tasks().isEmpty()
            ? " — NONE, so only "
                + proposable().stream()
                    .map(global -> "`" + global.id() + "`")
                    .collect(Collectors.joining(", "))
                + " or `none` can apply:\n(none)"
            : " (use the id or alias verbatim):\n" + taskList);
  }

  /**
   * Only a task that EXISTS may be acted on; an id the model invented resolves to nothing and is
   * refused.
   */
  @Nullable String existingTask(String proposed) {
    if (proposed == null || proposed.isBlank()) {
      return null;
    }
    String canonical = stateService.canonicalTaskId(proposed.strip());
    return stateService.task(canonical).isPresent() ? canonical : null;
  }

  /**
   * One task per line, including what is legal for it — the model should not propose a refused
   * action.
   */
  private static String taskLine(TaskView task) {
    String legal =
        task.actions().stream().map(TaskView.ActionView::id).collect(Collectors.joining(","));
    return "- id="
        + task.id()
        + " alias="
        + (task.alias() == null ? "-" : task.alias())
        + " status="
        + task.status()
        + " title=\""
        + (task.title() == null ? "" : task.title())
        + "\" legal="
        + legal;
  }
}

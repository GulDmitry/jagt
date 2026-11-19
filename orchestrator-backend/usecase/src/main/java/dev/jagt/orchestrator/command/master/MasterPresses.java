package dev.jagt.orchestrator.command.master;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.command.RootPresses;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Every verb a human presses, pressed as the Master: through the door a human's press uses, stamped
 * the Master's, kept only where {@code roles.master.mine} names it.
 */
@Service
@RequiredArgsConstructor
public class MasterPresses implements RootPresses {

  private final CommandService commands;
  private final GlobalCommands globals;
  private final ConfigService configService;

  @Override
  public String press(
      String taskIdOrAlias, String verb, @Nullable String project, @Nullable String line) {
    TaskAction action =
        TaskAction.byId(verb)
            .orElseThrow(() -> new Refusal(Refusal.Code.STATE, "No verb " + verb + " on a task."));
    if (project != null && line != null) {
      throw Refusal.byState("Name a project or a line, never both.");
    }
    return OriginContext.as(
        ActionOrigin.MASTER,
        () ->
            line == null
                ? commands.execute(taskIdOrAlias, action, project)
                : commands.executeLine(taskIdOrAlias, action, line));
  }

  @Override
  public String run(String verb, String tail) {
    GlobalCommand command =
        globals
            .byId(verb)
            .orElseThrow(() -> new Refusal(Refusal.Code.STATE, "No command " + verb + "."));
    command
        .whyNotTheMaster()
        .ifPresent(reason -> {
          throw new Refusal(
              Refusal.Code.ACTION_NOT_AVAILABLE,
              command.id() + " is never the Master's: " + reason + ".");
        });
    if (configService.load().master().profile().keeps(verb)) {
      throw new Refusal(
          Refusal.Code.ACTION_NOT_AVAILABLE,
          command.id() + " is kept for the human: mine names it.");
    }
    return OriginContext.as(ActionOrigin.MASTER, () -> command.run(tail));
  }
}

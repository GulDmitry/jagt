package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Every verb a human presses, pressed as the Master: through the door a human's press uses, stamped
 * the Master's, kept only where {@code roles.master.mine} names it.
 */
@Service
@RequiredArgsConstructor
public class MasterPresses {

  private final CommandService commands;
  private final GlobalCommands globals;
  private final ConfigService configService;

  public String press(String taskIdOrAlias, String verb, @Nullable String project) {
    TaskAction action =
        TaskAction.byId(verb)
            .orElseThrow(() -> new Refusal(Refusal.Code.STATE, "No verb " + verb + " on a task."));
    return OriginContext.as(
        ActionOrigin.MASTER, () -> commands.execute(taskIdOrAlias, action, project));
  }

  public String run(String verb, String tail) {
    GlobalCommand command =
        globals
            .byId(verb)
            .orElseThrow(() -> new Refusal(Refusal.Code.STATE, "No command " + verb + "."));
    command
        .whyNotTheMaster()
        .ifPresent(
            reason -> {
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

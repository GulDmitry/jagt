package dev.jagt.orchestrator.command.master;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.GlobalCommands;
import dev.jagt.orchestrator.flow.capability.TaskAction;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.master.MasterRight;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A word {@code roles.master.mine} names that is neither a step, a verb of a task nor a command
 * keeps nothing, so it is refused rather than ignored.
 */
@Component
@RequiredArgsConstructor
public class MasterMineCheck implements StartupCheck {

  private static final String MINE = "orchestrator.roles.master.mine";

  private final ConfigService configService;
  private final GlobalCommands globals;

  @Override
  public List<Problem> problems() {
    ConfigService.ConfigFile.ArbiterConfig master = configService.load().arbiter();
    if (!master.running()) {
      return List.of();
    }
    List<String> words = words();
    return master.mineOrDefault().stream()
        .filter(named -> !words.contains(named.strip().toLowerCase(Locale.ROOT)))
        .map(named ->
            new Problem(
                MINE, "orchestrator.roles.master.mine: '" + named + "' is not one of " + words))
        .toList();
  }

  private List<String> words() {
    return Stream.of(
            MasterRight.ids().stream(),
            Arrays.stream(TaskAction.values()).map(TaskAction::id),
            globals.all().stream().map(GlobalCommand::id))
        .flatMap(ids -> ids)
        .distinct()
        .sorted()
        .toList();
  }
}

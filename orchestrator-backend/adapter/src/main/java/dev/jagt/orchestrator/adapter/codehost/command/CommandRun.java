package dev.jagt.orchestrator.adapter.codehost.command;

import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs one of a project's own commands in its repository and tells a signed-out or missing one from
 * a refusal; exit 77 is signed out.
 */
public final class CommandRun {

  private static final int SIGNED_OUT = 77;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final Processes processes;
  private final Reach reach;
  private final String id;

  public CommandRun(Processes processes, Reach reach, String id) {
    this.processes = processes;
    this.reach = reach;
    this.id = id;
  }

  /**
   * {@code name} is the key of {@code template}; each placeholder in it is replaced by its value.
   */
  public Answer<String> run(
      HostSettings host, String name, List<String> template, Map<String, String> values) {
    return run(host, name, template, values, "");
  }

  /**
   * As above, with {@code last} the final argument, never read for a placeholder: free text goes
   * here.
   */
  public Answer<String> run(
      HostSettings host,
      String name,
      List<String> template,
      Map<String, String> values,
      String last) {
    List<String> command = new ArrayList<>();
    for (String part : template) {
      String filled = part;
      for (Map.Entry<String, String> value : values.entrySet()) {
        filled = filled.replace(value.getKey(), value.getValue());
      }
      command.add(filled);
    }
    if (!last.isEmpty()) {
      command.add(last);
    }
    List<String> resolved = new ArrayList<>(command);
    resolved.set(0, Executables.resolve(command.getFirst()));
    try {
      Processes.Result result = processes.run(host.dir(), TIMEOUT, resolved);
      if (result.exitCode() == 0) {
        reach.reached(id, host.project());
        return new Answer<>(Optional.of(result.stdout()), TokenUsage.NONE);
      }
      if (result.exitCode() == SIGNED_OUT) {
        return unreached(
            host, host.host().loginCommand(), host.key(name) + " says it is signed out");
      }
      String said = OneLine.clipped(result.stderr().isBlank() ? result.stdout() : result.stderr());
      return Answer.failed(
          TokenUsage.NONE,
          host.key(name)
              + " exited "
              + result.exitCode()
              + (said == null || said.isEmpty() ? "" : ": " + said));
    } catch (IllegalStateException e) {
      if (Executables.unresolved(resolved.getFirst())) {
        return unreached(
            host, List.of(), host.key(name) + ": '" + command.getFirst() + "' is not installed");
      }
      return Answer.failed(
          TokenUsage.NONE, host.key(name) + " did not run: " + OneLine.clipped(e.getMessage()));
    }
  }

  private Answer<String> unreached(HostSettings host, List<String> fix, String said) {
    ReachLost lost = new ReachLost(id, host.project(), fix, said);
    reach.lost(lost);
    return Answer.unreached(lost);
  }
}

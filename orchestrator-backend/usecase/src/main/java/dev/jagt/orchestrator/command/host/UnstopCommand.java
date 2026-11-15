package dev.jagt.orchestrator.command.host;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.service.stop.BoardStop;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnstopCommand implements GlobalCommand {

  private final BoardStop board;

  @Override
  public String id() {
    return "unstop";
  }

  @Override
  public int rank() {
    return 184;
  }

  @Override
  public String hint() {
    return "lift the board's stop: jobs and intake run again, sessions take their next prompt";
  }

  @Override
  public Optional<String> whyNotTheMaster() {
    return Optional.of("a stop on everything, the Master included, ends only by the human's lift");
  }

  @Override
  public String run(String tail) {
    return board.lift() ? "stop lifted: jobs and intake run again" : "the board was not stopped";
  }
}

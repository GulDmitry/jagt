package dev.jagt.orchestrator.command.host;

import dev.jagt.orchestrator.command.GlobalCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogCommand implements GlobalCommand {

  private final LogTail tail;

  @Override
  public String id() {
    return "tail";
  }

  @Override
  public int rank() {
    return 160;
  }

  @Override
  public String hint() {
    return "the last lines of jagt's own log: what it did and what failed";
  }

  @Override
  public boolean report() {
    return true;
  }

  @Override
  public String run(String tail) {
    return this.tail.render();
  }
}

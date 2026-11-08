package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ActStops;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActCommand implements GlobalCommand {

  private final ActStops actStops;

  @Override
  public String id() {
    return "act";
  }

  @Override
  public int rank() {
    return 185;
  }

  @Override
  public String hint() {
    return "give the Master back the buttons it stopped pressing";
  }

  @Override
  public Optional<String> whyNotTheMaster() {
    return Optional.of("a brake on the Master is not the Master's to lift");
  }

  @Override
  public String run(String tail) {
    return actStops.lift()
        ? "act restored: the Master presses again where `roles.master.mode` is act"
        : "act was not stopped";
  }
}

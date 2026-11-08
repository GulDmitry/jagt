package dev.jagt.orchestrator.command;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActivityCommand implements GlobalCommand {

  private final ActivityReport activity;

  @Override
  public String id() {
    return "activity";
  }

  @Override
  public int rank() {
    return 140;
  }

  @Override
  public String hint() {
    return "every status change of every task, live or finished, newest first";
  }

  @Override
  public List<String> usage() {
    return List.of("activity [created|shipped|deployed|stopped|done] [<project>]");
  }

  @Override
  public boolean report() {
    return true;
  }

  @Override
  public String run(String tail) {
    return activity.render(tail);
  }
}

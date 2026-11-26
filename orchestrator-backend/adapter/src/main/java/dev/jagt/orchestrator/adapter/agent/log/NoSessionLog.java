package dev.jagt.orchestrator.adapter.agent.log;

import dev.jagt.orchestrator.port.agent.SessionLog;
import java.nio.file.Path;

/** An agent CLI that keeps no log jagt reads spends nothing it can see. */
public class NoSessionLog implements SessionLog {

  @Override
  public Spent spent(Path log, long from, long limit) {
    return Spent.nothing(from);
  }
}

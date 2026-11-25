package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class FakeReach implements Reach {

  @Override
  public void lost(ReachLost lost) {}

  @Override
  public void lost(ReachLost lost, BooleanSupplier stands) {}

  @Override
  public void marked(ReachLost lost) {}

  @Override
  public void marked(ReachLost lost, BooleanSupplier stands) {}

  @Override
  public void reached(String driver, String host) {}

  @Override
  public List<ReachLost> standing() {
    return List.of();
  }
}

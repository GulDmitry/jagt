package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.port.JobFaults;
import java.util.ArrayList;
import java.util.List;

public final class FakeJobFaults implements JobFaults {

  private final List<String> causes = new ArrayList<>();

  public List<String> causes() {
    return List.copyOf(causes);
  }

  @Override
  public void faulted(String cause) {
    causes.add(cause);
  }

  @Override
  public Runnable carry(Runnable work) {
    return work;
  }
}

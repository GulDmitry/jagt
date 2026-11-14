package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ReadScopes;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

record FakeHearingProcesses(ReadScopes scopes, String tool, String detail, Result result)
    implements Processes {

  @Override
  public Result run(
      @Nullable Path workingDir, Duration timeout, Map<String, String> env, List<String> command) {
    String url = Objects.requireNonNull(env.get("JAGT_READ_GATE"));
    scopes.hear(url.substring(url.lastIndexOf('/') + 1), tool, detail);
    return result;
  }

  @Override
  public Result run(@Nullable Path workingDir, Duration timeout, List<String> command) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Result run(
      @Nullable Path workingDir,
      Duration timeout,
      Map<String, String> env,
      String stdin,
      List<String> command) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Process runDetached(@Nullable Path workingDir, List<String> command) {
    throw new UnsupportedOperationException();
  }
}

package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.port.host.Processes;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Every command succeeds with no output, but for the one that names the repository's folders. */
public final class FakeProcesses implements Processes {

  private static final Result CLEAN = new Result(0, "", "");

  private final Result folders;

  public FakeProcesses(Path folder) {
    this.folders = new Result(0, folder + "\n" + folder, "");
  }

  private Result answer(List<String> command) {
    return command.contains("rev-parse") ? folders : CLEAN;
  }

  @Override
  public Result run(@Nullable Path workingDir, Duration timeout, List<String> command) {
    return answer(command);
  }

  @Override
  public Result run(
      @Nullable Path workingDir, Duration timeout, Map<String, String> env, List<String> command) {
    return answer(command);
  }

  @Override
  public Result run(
      @Nullable Path workingDir,
      Duration timeout,
      Map<String, String> env,
      String stdin,
      List<String> command) {
    return answer(command);
  }

  @Override
  public Process start(@Nullable Path workingDir, List<String> command) {
    throw new UnsupportedOperationException(String.join(" ", command));
  }

  @Override
  public Process runDetached(@Nullable Path workingDir, List<String> command) {
    throw new UnsupportedOperationException(String.join(" ", command));
  }
}

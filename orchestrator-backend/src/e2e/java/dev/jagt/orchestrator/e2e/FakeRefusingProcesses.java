package dev.jagt.orchestrator.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.port.host.Processes;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public final class FakeRefusingProcesses extends ProcessRunner {

  public static final class UnstubbedHostCommand extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    UnstubbedHostCommand(List<String> command) {
      super(
          "A Spring test context started a host command no test stubbed: "
              + String.join(" ", command));
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Wiring {

    @Bean
    @Primary
    FakeRefusingProcesses refusingProcesses() {
      return new FakeRefusingProcesses(
          Set.of("git", "tmux", "ps", "pgrep", "lsof", "sh", "docker"));
    }
  }

  public static final class Check implements AfterEachCallback, AfterAllCallback {

    @Override
    public void afterEach(ExtensionContext context) {
      assertNoneRefused(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
      assertNoneRefused(context);
    }

    private static void assertNoneRefused(ExtensionContext context) {
      var refused =
          SpringExtension.getApplicationContext(context)
              .getBean(FakeRefusingProcesses.class)
              .drainRefused();
      assertThat(refused)
          .as("host commands a Spring test context started that no test stubbed")
          .isEmpty();
    }
  }

  private final Set<String> allowedPrograms;
  private final Queue<List<String>> refused = new ConcurrentLinkedQueue<>();

  public FakeRefusingProcesses(Set<String> allowedPrograms) {
    this.allowedPrograms = allowedPrograms;
  }

  public List<List<String>> drainRefused() {
    List<List<String>> drained = new ArrayList<>();
    for (List<String> command = refused.poll(); command != null; command = refused.poll()) {
      drained.add(command);
    }
    return drained;
  }

  @Override
  public Processes.Result run(@Nullable Path workingDir, Duration timeout, List<String> command) {
    refuseUnlessAllowed(command);
    return super.run(workingDir, timeout, command);
  }

  @Override
  public Processes.Result run(
      @Nullable Path workingDir, Duration timeout, Map<String, String> env, List<String> command) {
    refuseUnlessAllowed(command);
    return super.run(workingDir, timeout, env, command);
  }

  @Override
  public Processes.Result run(
      @Nullable Path workingDir,
      Duration timeout,
      Map<String, String> env,
      String stdin,
      List<String> command) {
    refuseUnlessAllowed(command);
    return super.run(workingDir, timeout, env, stdin, command);
  }

  @Override
  public Process start(@Nullable Path workingDir, List<String> command) {
    refuseUnlessAllowed(command);
    return super.start(workingDir, command);
  }

  @Override
  public Process runDetached(@Nullable Path workingDir, List<String> command) {
    refuseUnlessAllowed(command);
    return super.runDetached(workingDir, command);
  }

  private void refuseUnlessAllowed(List<String> command) {
    if (!allowedPrograms.contains(Path.of(command.get(0)).getFileName().toString())) {
      refused.add(List.copyOf(command));
      throw new UnstubbedHostCommand(command);
    }
  }
}

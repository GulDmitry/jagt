package dev.jagt.orchestrator.testing;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * A child alive when the suite ends holds the build's pipes open: it is killed and the suite fails.
 */
public final class NoChildLeftBehind implements LauncherSessionListener {

  private final Supplier<Stream<ProcessHandle>> children;

  public NoChildLeftBehind() {
    this(() -> ProcessHandle.current().descendants());
  }

  public NoChildLeftBehind(Supplier<Stream<ProcessHandle>> children) {
    this.children = children;
  }

  @Override
  public void launcherSessionClosed(LauncherSession session) {
    List<ProcessHandle> leaked = children.get().filter(ProcessHandle::isAlive).toList();
    List<String> described =
        leaked.stream()
            .map(child -> child.pid() + " " + child.info().command().orElse("?"))
            .toList();
    leaked.forEach(ProcessHandle::destroyForcibly);
    if (!leaked.isEmpty()) {
      throw new IllegalStateException("a test left child processes behind: " + described);
    }
  }
}

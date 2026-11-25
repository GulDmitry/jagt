package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.port.host.Host;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record FakeHost(Path home, Path workingDir, Path temp) implements Host {

  public static FakeHost machine() {
    return new FakeHost(
        Path.of(System.getProperty("user.home")),
        Path.of(System.getProperty("user.dir")),
        Path.of(System.getProperty("java.io.tmpdir")));
  }

  public FakeHost withHome(Path home) {
    return new FakeHost(home, workingDir, temp);
  }

  public FakeHost withTemp(Path temp) {
    return new FakeHost(home, workingDir, temp);
  }

  @Override
  public String osName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<String> env(String name) {
    return Optional.ofNullable(System.getenv(name));
  }

  @Override
  public boolean onPath(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String resolve(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Path> applicationDirs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Path> jar() {
    return Optional.empty();
  }
}

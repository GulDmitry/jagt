package dev.jagt.orchestrator.job.host;

import dev.jagt.orchestrator.port.host.ReleaseFeed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class FakeReleaseFeed implements ReleaseFeed {

  private final Optional<String> latest;
  private final String jarBytes;
  private final String sums;
  private final boolean unreachable;
  int downloads;

  FakeReleaseFeed(Optional<String> latest, String jarBytes, String sums, boolean unreachable) {
    this.latest = latest;
    this.jarBytes = jarBytes;
    this.sums = sums;
    this.unreachable = unreachable;
  }

  @Override
  public String driver() {
    return "fake";
  }

  @Override
  public String host() {
    return "releases.example.invalid";
  }

  @Override
  public Optional<String> latest() throws IOException {
    if (unreachable) {
      throw new IOException("connection refused");
    }
    return latest;
  }

  @Override
  public void jar(String version, Path target) throws IOException {
    downloads++;
    Files.writeString(target, jarBytes);
  }

  @Override
  public void checksums(String version, Path target) throws IOException {
    Files.writeString(target, sums);
  }
}

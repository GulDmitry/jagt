package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

public final class ServerRun implements AutoCloseable {

  private final Path root;
  private final StandTeardown teardown;
  private final Instant began = Instant.now(Clock.systemUTC());
  private JagtEngine jagt;
  private StandMarker marker;

  ServerRun(Path root, StandMarker marker, JagtEngine jagt, StandTeardown teardown) {
    this.root = root;
    this.marker = marker;
    this.jagt = jagt;
    this.teardown = teardown;
  }

  public static ServerRun start(String scenario) throws IOException {
    return StandLauncher.standard().server(scenario);
  }

  public Path root() {
    return root;
  }

  public JagtEngine engine() {
    return jagt;
  }

  public StandBoard board() {
    return jagt.board();
  }

  public void restart(Restart how) {
    jagt = jagt.restart(how);
    marker = marker.withJagt(jagt.pid(), jagt.port()).writeTo(root);
  }

  public void passed() {
    marker = marker.withOutcome(StandMarker.PASSED);
  }

  @Override
  public void close() throws IOException {
    try {
      Files.writeString(root.resolve("screens.log"), jagt.screens());
      jagt.close();
      teardown.keep(root, marker, null, true, began);
    } finally {
      teardown.release();
    }
  }
}

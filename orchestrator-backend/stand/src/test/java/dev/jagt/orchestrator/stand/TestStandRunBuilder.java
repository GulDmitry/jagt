package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.With;
import org.jspecify.annotations.Nullable;

@With
public record TestStandRunBuilder(Path root, @Nullable Path replayed, Duration poll) {

  public static TestStandRunBuilder standRun(Path root) {
    return new TestStandRunBuilder(root, null, Duration.ofMillis(1));
  }

  public StandRun build(JagtEngine jagt) throws IOException {
    Files.createDirectories(root);
    Path base = Objects.requireNonNull(root.getParent());
    StandTeardown teardown =
        new StandTeardown(
            new StandArchive(
                base.resolve("runs"),
                base.resolve("recordings"),
                base.resolve("failed"),
                (run, result) -> {},
                parent ->
                    new StandTraces(parent, parent.resolve("claude.json"), parent.resolve("p"))),
            new StandSlots(1),
            new PrintStream(OutputStream.nullOutputStream()),
            poll);
    StandSite site =
        new StandSite(
            root,
            StandProject.create(root),
            new StandHost(root.resolve("host"), InetAddress.getLoopbackAddress()),
            Optional.empty());
    return new StandRun(
        site, StandMarker.started("happy/judge"), StandMode.JUDGE, jagt, replayed, teardown, poll);
  }
}

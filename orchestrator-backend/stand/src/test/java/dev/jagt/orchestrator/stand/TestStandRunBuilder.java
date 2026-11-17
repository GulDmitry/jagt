package dev.jagt.orchestrator.stand;

import java.io.IOException;
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
        TestTeardownBuilder.teardown(base).withPoll(poll).build(new StandSlots(1));
    StandSite site =
        new StandSite(
            root,
            StandProject.create(root),
            StandHost.listen(root.resolve("host"), InetAddress.getLoopbackAddress()),
            Optional.empty());
    return new StandRun(
        site, StandMarker.started("happy/judge"), StandMode.JUDGE, jagt, replayed, teardown, poll);
  }
}

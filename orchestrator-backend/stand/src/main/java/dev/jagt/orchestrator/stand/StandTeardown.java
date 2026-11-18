package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.stand.invariant.Violation;
import dev.jagt.orchestrator.stand.replay.Recorder;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class StandTeardown {

  private final StandArchive archive;
  private final StandSlots slots;
  private final PrintStream out;
  private final Duration poll;

  StandTeardown(StandArchive archive, StandSlots slots, PrintStream out, Duration poll) {
    this.archive = archive;
    this.slots = slots;
    this.out = out;
    this.poll = poll;
  }

  void take() {
    slots.take();
  }

  void release() {
    slots.free();
  }

  void keep(
      Path root, StandMarker marker, @Nullable StandOutcome last, boolean replayed, Instant began) {
    printUsage(root, marker.scenario());
    if (!replayed) {
      keepRecording(root, marker.scenario());
    }
    boolean suspended = last != null && last.suspended();
    StandMarker settled = suspended ? marker.withOutcome(StandMarker.SUSPENDED) : marker;
    boolean passed = settled.outcome().equals(StandMarker.PASSED);
    archive
        .report()
        .accept(
            settled.scenario(),
            passed ? StandMarker.PASSED : suspended ? StandMarker.SUSPENDED : StandMarker.FAILED);
    keepRunRecord(root, settled, last, replayed, began);
    if (suspended) {
      out.println(
          "stand run "
              + settled.scenario()
              + " host suspended: "
              + Objects.requireNonNull(last).detail());
    } else if (!passed) {
      settled.withOutcome(StandMarker.FAILED).writeTo(root);
      out.println(
          "stand run "
              + settled.scenario()
              + " failed; evidence: "
              + StandEvidence.keep(root, settled.scenario(), archive.failed()).toAbsolutePath());
    }
    StandShell.deleteRoot(root);
    StandTmuxServers.stopOrphaned(Objects.requireNonNull(root.getParent()));
    archive
        .traces()
        .apply(Objects.requireNonNull(root.getParent()))
        .remove(root.getFileName().toString()::equals);
  }

  private void keepRunRecord(
      Path root, StandMarker marker, @Nullable StandOutcome last, boolean replayed, Instant began) {
    List<String> found;
    try {
      found = StandBooks.at(root).violations().stream().map(Violation::toString).toList();
    } catch (RuntimeException e) {
      found = List.of("invariants unreadable: " + e);
    }
    String[] named = marker.scenario().split("/", 2);
    StandRunLog.keep(
        archive.runs(),
        new StandRunLog.Entry(
            Instant.now(Clock.systemUTC()),
            named[0],
            named.length > 1 ? named[1] : "",
            replayed ? "replay" : "live",
            new StandRunLog.Verdict(
                marker.outcome().equals(StandMarker.PASSED),
                last == null ? "not driven" : last.ending().words(),
                last == null ? "" : last.detail(),
                found),
            Duration.between(began, Instant.now(Clock.systemUTC())).toSeconds(),
            StandSpend.read(root)),
        root.getFileName().toString());
  }

  private void keepRecording(Path root, String scenario) {
    try {
      Path kept =
          Files.createDirectories(archive.recordings())
              .resolve(scenario.replace('/', '-') + "-" + root.getFileName() + ".jsonl");
      Files.copy(Recorder.write(root, poll), kept, StandardCopyOption.REPLACE_EXISTING);
      StandRecordings.fold(archive.recordings());
      out.println("stand run " + scenario + " recorded: " + kept.toAbsolutePath());
    } catch (IOException | RuntimeException e) {
      out.println("stand run " + scenario + " not recorded: " + e);
    }
  }

  private void printUsage(Path root, String scenario) {
    Path usage = root.resolve("records/usage-by-day.jsonl");
    try {
      String spent = Files.isRegularFile(usage) ? Files.readString(usage).strip() : "none recorded";
      out.println("stand run " + scenario + " usage: " + spent);
    } catch (IOException e) {
      out.println("stand run " + scenario + " usage unreadable: " + e.getMessage());
    }
  }
}

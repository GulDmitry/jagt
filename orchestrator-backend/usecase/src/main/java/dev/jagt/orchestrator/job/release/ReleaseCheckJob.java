package dev.jagt.orchestrator.job.release;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.port.host.Host;
import dev.jagt.orchestrator.port.host.ReleaseFeed;
import dev.jagt.orchestrator.port.reach.Reach;
import dev.jagt.orchestrator.port.reach.ReachLost;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

/**
 * Keeps the newest release beside the running one, verified, and changes nothing else: only a jar
 * running from {@code <root>/versions/<v>/} has anywhere to keep it.
 */
@Service
@Slf4j
public class ReleaseCheckJob implements Job {

  private static final String VERSIONS = "versions";
  private static final String JAR = "jagt.jar";
  private static final Pattern VERSION = Pattern.compile("\\d{1,9}(\\.\\d{1,9})*");

  private final Host host;
  private final ReleaseFeed feed;
  private final Reach reach;

  public ReleaseCheckJob(Host host, ReleaseFeed feed, Reach reach) {
    this.host = host;
    this.feed = feed;
    this.reach = reach;
  }

  /** The running version, and the folder holding every installed one. */
  private record Installed(String running, Path versions, Path root) {}

  @Override
  public String id() {
    return "releasecheck";
  }

  @Override
  public String describe() {
    return "keep a newer release verified beside the running one";
  }

  @Override
  public Duration every() {
    return Duration.ofHours(1);
  }

  @Override
  public void run() {
    installed().ifPresent(this::check);
  }

  private Optional<Installed> installed() {
    return host.jar()
        .filter(jar -> JAR.equals(String.valueOf(jar.getFileName())))
        .flatMap(ReleaseCheckJob::within);
  }

  private static Optional<Installed> within(Path jar) {
    Path folder = jar.getParent();
    Path versions = folder == null ? null : folder.getParent();
    Path root = versions == null ? null : versions.getParent();
    if (folder == null
        || versions == null
        || root == null
        || !VERSIONS.equals(String.valueOf(versions.getFileName()))) {
      return Optional.empty();
    }
    return Optional.of(new Installed(String.valueOf(folder.getFileName()), versions, root));
  }

  private void check(Installed installed) {
    sweepLeftovers(installed.root());
    Optional<String> latest;
    try {
      latest = feed.latest();
      reach.reached(feed.driver(), feed.host());
    } catch (IOException e) {
      reach.marked(
          new ReachLost(
              feed.driver(),
              feed.host(),
              List.of(),
              feed.driver() + " cannot read the release feed on " + feed.host()));
      log.atWarn()
          .setMessage("release read failed")
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "no newer release looked for until the next run")
          .setCause(e)
          .log();
      return;
    }
    latest
        .filter(version -> newer(version, installed.running()))
        .filter(version -> !Files.exists(installed.versions().resolve(version).resolve(JAR)))
        .ifPresent(version -> keep(installed.versions(), version));
  }

  private static boolean newer(String candidate, String running) {
    if (!VERSION.matcher(candidate).matches() || !VERSION.matcher(running).matches()) {
      return false;
    }
    long[] wanted = parts(candidate);
    long[] current = parts(running);
    for (int i = 0; i < Math.max(wanted.length, current.length); i++) {
      long a = i < wanted.length ? wanted[i] : 0;
      long b = i < current.length ? current[i] : 0;
      if (a != b) {
        return a > b;
      }
    }
    return false;
  }

  private static long[] parts(String version) {
    return Arrays.stream(version.split("\\.")).mapToLong(Long::parseLong).toArray();
  }

  private void keep(Path versions, String version) {
    @Nullable Path staging = null;
    try {
      staging = Files.createTempDirectory(versions.getParent(), ".staging.");
      Path ready = stage(staging, version);
      Files.move(ready, versions.resolve(version), StandardCopyOption.ATOMIC_MOVE);
      log.atInfo()
          .setMessage("release kept")
          .addKeyValue("ref", version)
          .addKeyValue("path", versions.resolve(version))
          .log();
    } catch (IOException e) {
      log.atWarn()
          .setMessage("release discarded")
          .addKeyValue("ref", version)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "nothing kept, the installed versions stay as they were")
          .setCause(e)
          .log();
    } finally {
      sweep(staging);
    }
  }

  private Path stage(Path staging, String version) throws IOException {
    String name = "jagt-" + version + ".jar";
    Path jar = staging.resolve(name);
    Path sums = staging.resolve("jagt-" + version + ".sha256");
    feed.jar(version, jar);
    feed.checksums(version, sums);
    String want = Sha256.listed(sums, name);
    String got = Sha256.of(jar);
    if (!got.equalsIgnoreCase(want)) {
      throw new IOException("checksum mismatch for " + name);
    }
    Path folder = Files.createDirectory(staging.resolve(version));
    Files.move(jar, folder.resolve(JAR));
    return folder;
  }

  private static void sweepLeftovers(Path root) {
    try (DirectoryStream<Path> leftovers = Files.newDirectoryStream(root, ".staging.*")) {
      leftovers.forEach(ReleaseCheckJob::sweep);
    } catch (IOException e) {
      log.atWarn()
          .setMessage("staging leftovers not listed")
          .addKeyValue("path", root)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "an interrupted run's hidden folders stay until the next check")
          .setCause(e)
          .log();
    }
  }

  private static void sweep(@Nullable Path staging) {
    if (staging == null) {
      return;
    }
    try {
      FileSystemUtils.deleteRecursively(staging);
    } catch (IOException e) {
      log.atWarn()
          .setMessage("staging left behind")
          .addKeyValue("path", staging)
          .addKeyValue("cause", e.toString())
          .addKeyValue("effect", "a hidden folder stays in the install root")
          .setCause(e)
          .log();
    }
  }
}

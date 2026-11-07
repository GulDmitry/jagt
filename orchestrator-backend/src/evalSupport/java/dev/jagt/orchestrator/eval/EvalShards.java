package dev.jagt.orchestrator.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EvalShards {

  private static final Pattern SHARD_CLASS = Pattern.compile(".*Shard(\\d+)Test");

  private record Held(int index, FileChannel channel) {}

  private final Path base;
  private final int shards;
  private Held held;

  private EvalShards(Path base, int shards) {
    this.base = base;
    this.shards = shards;
  }

  public static EvalShards under(Path base, int shards) {
    return new EvalShards(base, shards);
  }

  public synchronized int claim(Class<?> shardClass) {
    if (held == null) {
      held = claimOwn(indexOf(shardClass));
    }
    return held.index();
  }

  public synchronized int claimed() {
    if (held == null) {
      throw new IllegalStateException(
          "No shard is claimed yet: the shard class claims its own in beforeAll.");
    }
    return held.index();
  }

  static int indexOf(Class<?> shardClass) {
    Matcher number = SHARD_CLASS.matcher(shardClass.getSimpleName());
    if (!number.matches()) {
      throw new IllegalArgumentException(
          shardClass.getName() + " is not named <Name>Shard<N>Test.");
    }
    return Integer.parseInt(number.group(1));
  }

  public synchronized void release() throws IOException {
    if (held != null) {
      held.channel().close();
    }
  }

  private Held claimOwn(int index) {
    if (index >= shards) {
      throw new IllegalStateException(
          "Shard " + index + " is past the " + shards + " this run was given.");
    }
    try {
      Files.createDirectories(base);
      FileChannel channel =
          FileChannel.open(
              base.resolve("shard-" + index + ".lock"),
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE);
      try {
        if (channel.tryLock() != null) {
          return new Held(index, channel);
        }
      } catch (OverlappingFileLockException heldByThisJvm) {
        channel.close();
        throw heldAlready(index);
      }
      channel.close();
    } catch (IOException unclaimable) {
      throw new UncheckedIOException(unclaimable);
    }
    throw heldAlready(index);
  }

  private IllegalStateException heldAlready(int index) {
    return new IllegalStateException(
        "Another eval run holds shard "
            + index
            + " of "
            + base
            + " — one session cannot serve two. Wait for it, or end it.");
  }
}

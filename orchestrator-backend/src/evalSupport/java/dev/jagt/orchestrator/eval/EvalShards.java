package dev.jagt.orchestrator.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class EvalShards {

    private record Held(int index, FileChannel channel) {
    }

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

    public synchronized int claim() {
        if (held == null) {
            held = claimFree();
        }
        return held.index();
    }

    public synchronized void release() throws IOException {
        if (held != null) {
            held.channel().close();
        }
    }

    private Held claimFree() {
        try {
            Files.createDirectories(base);
            for (int index = 0; index < shards; index++) {
                FileChannel channel = FileChannel.open(base.resolve("shard-" + index + ".lock"),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                try {
                    if (channel.tryLock() != null) {
                        return new Held(index, channel);
                    }
                } catch (OverlappingFileLockException heldByThisJvm) {
                    channel.close();
                    continue;
                }
                channel.close();
            }
        } catch (IOException unclaimable) {
            throw new UncheckedIOException(unclaimable);
        }
        throw new IllegalStateException("Another eval run holds every shard of " + base
                + " — one session cannot serve two. Wait for it, or end it.");
    }
}

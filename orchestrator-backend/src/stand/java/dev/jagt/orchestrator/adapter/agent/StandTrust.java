package dev.jagt.orchestrator.adapter.agent;

import java.nio.file.Path;

public final class StandTrust {

    private StandTrust() {
    }

    public static Path configFile() {
        return ClaudeTrust.configFile();
    }

    public static void forget(Path configFile, Path worktree) {
        ClaudeTrust.forget(configFile, worktree);
    }
}

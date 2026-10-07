package dev.jagt.orchestrator.adapter.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A session record holding no word of the human's, as a worktree whose session nobody typed into has. */
public final class MasterEvalTranscripts {

    private MasterEvalTranscripts() {
    }

    public static void nothingTypedIn(Path worktree) throws IOException {
        Files.createDirectories(ClaudeTranscripts.projectsDir().resolve(ClaudeTranscripts.slug(worktree)));
    }
}

package dev.jagt.orchestrator.adapter.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

public final class MasterEvalTranscripts {

    private MasterEvalTranscripts() {
    }

    private static final JsonMapper JSON = new JsonMapper();

    public static void nothingTypedIn(Path worktree) throws IOException {
        Files.createDirectories(ClaudeTranscripts.projectsDir().resolve(ClaudeTranscripts.slug(worktree)));
    }

    public static void typedIn(Path worktree, List<String> said) throws IOException {
        Path dir = ClaudeTranscripts.projectsDir().resolve(ClaudeTranscripts.slug(worktree));
        Files.createDirectories(dir);
        List<String> lines = said.stream().map(text -> JSON.writeValueAsString(Map.of("type", "user",
                "entrypoint", "cli", "origin", Map.of("kind", "human"), "message", Map.of("content", text)))).toList();
        Files.write(dir.resolve("eval-session.jsonl"), lines);
    }
}

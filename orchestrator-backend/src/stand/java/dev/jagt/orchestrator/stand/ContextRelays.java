package dev.jagt.orchestrator.stand;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.task.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

public final class ContextRelays {

    private final SessionBrief brief;
    private final Map<String, String> seen = new HashMap<>();

    public ContextRelays(SessionBrief brief) {
        this.brief = brief;
    }

    public Optional<List<String>> changed(String task, JsonNode state) {
        String worktree = state.path("repos").path(0).path("worktreePath").asString("");
        Path context = Path.of(worktree).resolve(Artifact.CONTEXT.fileName());
        if (worktree.isEmpty() || !Files.isRegularFile(context)) {
            return Optional.empty();
        }
        String text = read(context);
        if (text.equals(seen.put(task, text))) {
            return Optional.empty();
        }
        return Optional.of(steps(text));
    }

    private List<String> steps(String text) {
        return brief.steps().stream().filter(step -> brief.section(step).filter(body -> !body.isBlank())
                .filter(text::contains).isPresent()).toList();
    }

    private static String read(Path context) {
        try {
            return Files.readString(context);
        } catch (IOException e) {
            throw new StandFailure("cannot read " + context, e);
        }
    }
}

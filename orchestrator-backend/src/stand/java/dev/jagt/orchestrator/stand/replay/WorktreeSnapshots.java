package dev.jagt.orchestrator.stand.replay;

import dev.jagt.orchestrator.stand.StandFailure;
import dev.jagt.orchestrator.task.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** What jagt keeps of a round in no log: each task's stored verdict and the files its session writes for jagt. */
public final class WorktreeSnapshots {

    static final String FILE = "snapshots.jsonl";
    static final String REVIEW = ".jagt/master-review.json";
    static final List<String> SESSION_FILES = List.of(Artifact.PLAN.fileName(), Artifact.VISUAL.fileName(),
            Artifact.NOTES.fileName(), Artifact.REPLIES.fileName());
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final Path file;
    private final Map<String, String> last = new HashMap<>();

    public WorktreeSnapshots(Path root) {
        this.file = root.resolve(FILE);
    }

    public void take(JsonNode state) {
        state.path("tasks").properties().forEach(task -> {
            String worktree = task.getValue().path("repos").path(0).path("worktreePath").asString("");
            if (!worktree.isEmpty()) {
                take(task.getKey(), Path.of(worktree), REVIEW);
                SESSION_FILES.forEach(name -> take(task.getKey(), Path.of(worktree), name));
            }
        });
    }

    private void take(String task, Path worktree, String name) {
        Path read = worktree.resolve(name);
        if (!Files.isRegularFile(read)) {
            return;
        }
        try {
            String text = Files.readString(read);
            if (text.equals(last.put(task + " " + name, text))) {
                return;
            }
            ObjectNode line = JSON.createObjectNode().put("at", Instant.now().toString()).put("task", task)
                    .put("file", name);
            JsonNode content = name.equals(REVIEW) ? line.set("review", JSON.readTree(text)) : line.put("content", text);
            Files.writeString(file, content + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new StandFailure("cannot snapshot " + read, e);
        }
    }
}

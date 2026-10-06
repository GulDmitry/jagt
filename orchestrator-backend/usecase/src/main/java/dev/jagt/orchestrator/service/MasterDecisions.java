package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** What this task's earlier rounds settled, kept beside the review so every later reader starts from it. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDecisions {

    private final WorktreeChanges changes;
    /** The tree each worktree's last answer was given over. */
    private final Map<String, String> answeredOver = new ConcurrentHashMap<>();

    public void answered(TaskState task) {
        changes.state(task).ifPresent(state -> answeredOver.put(task.worktreePath(), state));
    }

    /** Asked again over the tree its last answer was given on, the answer was not one a session could act on. */
    public boolean answeredOverThisTree(TaskState task) {
        String then = answeredOver.get(task.worktreePath());
        return then != null && changes.state(task).map(then::equals).orElse(false);
    }

    public void record(TaskState task, String decided) {
        Path file = Path.of(task.worktreePath()).resolve(Artifact.DECISIONS.fileName());
        try {
            Files.writeString(file, decided.strip() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException unwritable) {
            log.atWarn().setMessage("master decision unrecorded").addKeyValue("file", file)
                    .addKeyValue("cause", unwritable.toString())
                    .addKeyValue("effect", "the next round may reopen it")
                    .log();
        }
    }

    /** Blank where nothing is settled yet. */
    public String of(TaskState task) {
        Path file = Path.of(task.worktreePath()).resolve(Artifact.DECISIONS.fileName());
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return Files.readString(file).strip();
        } catch (IOException unreadable) {
            log.atWarn().setMessage("master decisions unreadable").addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return "";
        }
    }
}

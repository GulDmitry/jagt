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

    /** Answers over one unchanged tree after which the session could act on none: the approach changes. */
    static final int ANSWERS_PER_TREE = 3;
    /** Answers over one unchanged tree past which the loop is a runaway, the one stop that reaches the human. */
    static final int RUNAWAY = 9;

    private final WorktreeChanges changes;
    private final Map<String, Answered> answeredOver = new ConcurrentHashMap<>();

    private record Answered(String tree, int times) {
    }

    public void answered(TaskState task) {
        changes.state(task).ifPresent(tree -> answeredOver.merge(task.worktreePath(), new Answered(tree, 1),
                (then, now) -> then.tree().equals(tree) ? new Answered(tree, then.times() + 1) : now));
    }

    /** How many answers this tree has already had, none where it changed since the last. */
    public int answersOverThisTree(TaskState task) {
        Answered then = answeredOver.get(task.worktreePath());
        return then != null && changes.state(task).map(then.tree()::equals).orElse(false) ? then.times() : 0;
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

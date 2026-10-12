package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.service.WorktreeChanges;
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
    public static final int ANSWERS_PER_TREE = 3;
    /** Answers over one unchanged tree past which the loop is a runaway, the one stop that reaches the human. */
    public static final int RUNAWAY = 9;
    /** Not-ready rounds running holding only repeats, at which the loop is the human's rather than the session's. */
    public static final int REPEATED_ROUNDS = 2;

    private final WorktreeChanges changes;
    /** Never pruned: one small entry per worktree the Master ever answered in, for the life of the process. */
    private final Map<String, Answered> answeredOver = new ConcurrentHashMap<>();
    private final Map<String, Repeated> repeatedRounds = new ConcurrentHashMap<>();

    private record Answered(String tree, int times) {
    }

    private record Repeated(long verdictAt, int rounds) {
    }

    /** Not-ready rounds running that held only repeats, this one counted once however often it is acted on. */
    public int returned(TaskState task, long verdictAt, boolean onlyRepeats) {
        if (!onlyRepeats) {
            repeatedRounds.remove(task.worktreePath());
            return 0;
        }
        return repeatedRounds.merge(task.worktreePath(), new Repeated(verdictAt, 1),
                (then, now) -> then.verdictAt() == verdictAt ? then : new Repeated(verdictAt, then.rounds() + 1))
                .rounds();
    }

    public boolean repeats(TaskState task, String finding) {
        return FindingMatch.repeats(finding, of(task).lines().toList());
    }

    public int repeatedRounds(TaskState task) {
        Repeated then = repeatedRounds.get(task.worktreePath());
        return then == null ? 0 : then.rounds();
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

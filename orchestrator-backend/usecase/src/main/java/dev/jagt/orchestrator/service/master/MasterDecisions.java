package dev.jagt.orchestrator.service.master;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jagt.orchestrator.service.WorktreeChanges;
import dev.jagt.orchestrator.service.master.MasterReview.Identity;
import dev.jagt.orchestrator.service.master.MasterReview.Said;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** What this task's earlier rounds settled, kept in its worktree so every later reader, after a restart too, starts from it. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDecisions {

    /** Answers over one unchanged tree after which the session could act on none: the approach changes. */
    public static final int ANSWERS_PER_TREE = 3;
    /** Answers over one unchanged tree past which the loop is a runaway, the one stop that reaches the human. */
    public static final int RUNAWAY = 9;
    /** Past every count above, so folding older entries changes no answer. */
    static final int KEPT = 20;
    static final String STORED = "master-rounds.json";

    private final WorktreeChanges changes;

    /** {@code settled}: what the round ruled, requests for evidence left out. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Round(long round, List<Identity> settled) {
    }

    /** {@code answers}: the tree each answer was given over, oldest first. */
    record Rounds(List<Round> rounds, List<String> answers) {

        static final Rounds NONE = new Rounds(List.of(), List.of());
    }

    /** The findings of {@code findings} an earlier round already made, whatever is recorded of this one. */
    public synchronized Set<Identity> repeated(TaskState task, long round, List<Said> findings) {
        return seen(earlier(read(task), round), findings);
    }

    /** Records the round; a round acted on again is counted once. Answers what {@link #repeated} does. */
    public synchronized Set<Identity> returned(TaskState task, long round, List<Said> findings) {
        Rounds before = read(task);
        List<Round> earlier = earlier(before, round);
        Set<Identity> repeated = seen(earlier, findings);
        List<Round> now = last(Stream.concat(earlier.stream(), Stream.of(new Round(round,
                findings.stream().filter(Said::settles).map(Said::identity).toList()))).toList());
        write(task, new Rounds(now, before.answers()));
        return repeated;
    }

    private static List<Round> earlier(Rounds before, long round) {
        return before.rounds().stream().filter(then -> then.round() != round).toList();
    }

    private static Set<Identity> seen(List<Round> earlier, List<Said> findings) {
        Set<Identity> seen = earlier.stream().flatMap(then -> then.settled().stream()).collect(Collectors.toSet());
        return findings.stream().map(Said::identity).filter(seen::contains).collect(Collectors.toSet());
    }

    public synchronized void answered(TaskState task) {
        changes.state(task).ifPresent(tree -> {
            Rounds before = read(task);
            write(task, new Rounds(before.rounds(),
                    last(Stream.concat(before.answers().stream(), Stream.of(tree)).toList())));
        });
    }

    /** How many answers this tree has already had, none where it changed since the last. */
    public int answersOverThisTree(TaskState task) {
        Optional<String> tree = changes.state(task);
        List<String> answers = read(task).answers();
        int given = 0;
        while (tree.isPresent() && given < answers.size()
                && answers.get(answers.size() - 1 - given).equals(tree.get())) {
            given++;
        }
        return given;
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

    private static <T> List<T> last(List<T> all) {
        return new ArrayList<>(all.subList(Math.max(0, all.size() - KEPT), all.size()));
    }

    private static Rounds read(TaskState task) {
        return JagtJson.read(JagtJson.in(String.valueOf(task.worktreePath()), STORED), Rounds.class)
                .orElse(Rounds.NONE);
    }

    private static void write(TaskState task, Rounds rounds) {
        JagtJson.write(JagtJson.in(String.valueOf(task.worktreePath()), STORED), rounds);
    }
}

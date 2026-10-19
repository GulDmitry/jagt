package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.service.FileStamps;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the Master session leaves behind about one task. A file in that task's own worktree, so it sits in the
 * artifact chain beside the diff and the drafted replies, and `ide <task>` opens all of it at once. It is the
 * ONE thing the reviewer writes: everything else in a worktree belongs to whoever works there.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterReview {

    /** In the worktree, beside the drafted replies, and read back by jagt rather than reported over a wire. */
    public static final String FILE = Artifact.REVIEW.fileName();

    /** The last line the reviewer writes, and the only part of the file jagt reads as an answer. */
    private static final String VERDICT = "VERDICT:";

    /** A heading line, so it is never a finding. */
    public static final String OWN_DIFF = "# own-diff: ";

    /** What the reviewer concluded, or empty where it has not written, or has not finished. */
    public Optional<Verdict> of(TaskState task) {
        Path file = file(task);
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(file).stream().map(String::strip).toList();
            int verdictAt = lines.size() - 1;
            while (verdictAt >= 0 && !lines.get(verdictAt).startsWith(VERDICT)) {
                verdictAt--;
            }
            if (verdictAt < 0) {
                return Optional.empty();
            }
            List<String> findings = lines.subList(0, verdictAt).stream()
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            String ownDiff = lines.stream().filter(line -> line.startsWith(OWN_DIFF)).findFirst()
                    .map(line -> line.substring(OWN_DIFF.length())).orElse("");
            return Optional.of(Verdict.of(lines.get(verdictAt).substring(VERDICT.length()), findings,
                    FileStamps.modified(file), ownDiff));
        } catch (IOException | RuntimeException unreadable) {
            log.atWarn().setMessage("master review unreadable")
                    .addKeyValue("file", file)
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return Optional.empty();
        }
    }

    /** Makes the verdict answer the task as it stands now; false where the file could not be touched. */
    public boolean restamp(TaskState task) {
        try {
            Files.setLastModifiedTime(file(task), FileTime.fromMillis(System.currentTimeMillis()));
            return true;
        } catch (IOException | RuntimeException unwritable) {
            log.atWarn().setMessage("master review unstamped").addKeyValue("file", file(task))
                    .addKeyValue("cause", unwritable.toString()).log();
            return false;
        }
    }

    /** Whether the reviewer has looked at the task AS IT STANDS, rather than at an earlier hand-back. */
    public boolean readsTheRoundInFront(TaskState task) {
        return of(task).filter(verdict -> verdict.answers(task)).isPresent();
    }

    public @Nullable Path file(TaskState task) {
        String worktree = task.worktreePath();
        return worktree == null || worktree.isBlank() ? null : Path.of(worktree).resolve(FILE);
    }

    public enum Kind { READY, NOT_READY, QUESTION }

    /** {@code findings} are the lines above the verdict, headings left out. */
    public record Verdict(Kind kind, List<String> findings, long writtenAt, String ownDiff) {

        public Verdict(Kind kind, List<String> findings, long writtenAt) {
            this(kind, findings, writtenAt, "");
        }

        /** Ready beside anything but the one line saying nothing is wrong is a finding waved through. */
        static Verdict of(String word, List<String> findings, long writtenAt, String ownDiff) {
            String said = word.strip();
            if (said.equalsIgnoreCase("question")) {
                return new Verdict(Kind.QUESTION, findings, writtenAt, ownDiff);
            }
            boolean ready = said.equalsIgnoreCase("ready") && findings.size() <= 1;
            return new Verdict(ready ? Kind.READY : Kind.NOT_READY, findings, writtenAt, ownDiff);
        }

        /** Written about the task as it stands, rather than about an earlier hand-back. */
        public boolean answers(TaskState task) {
            return writtenAt >= task.statusSince();
        }

        public boolean ready() {
            return kind == Kind.READY;
        }

        public String said() {
            return kind.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }

        public String question() {
            return findings.isEmpty() ? "" : findings.getLast();
        }
    }
}

package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.TaskState;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What the Master session leaves behind about one task: every role's answer as jagt received it, and the same
 * rendered for a human beside the diff, which nothing reads back. It is the ONE thing the reviewer writes.
 */
@Service
@Slf4j
public class MasterReview {

    public static final String FILE = Artifact.REVIEW.fileName();
    static final String STORED = "master-review.json";

    /** What the reviewer concluded, or empty where it has not written. */
    public Optional<Verdict> of(TaskState task) {
        return stored(task).map(Verdict::of);
    }

    Optional<Review> stored(TaskState task) {
        String worktree = task.worktreePath();
        return worktree == null || worktree.isBlank() ? Optional.empty()
                : JagtJson.read(JagtJson.in(worktree, STORED), Review.class);
    }

    /** False where either file could not be written; the typed record is written last, so it never runs ahead. */
    public boolean write(TaskState task, Review review, String markdown) {
        Path file = file(task);
        if (file == null) {
            return false;
        }
        try {
            Files.writeString(file, markdown);
        } catch (IOException unwritable) {
            log.atError().setMessage("master review unwritable").addKeyValue("file", file)
                    .addKeyValue("cause", unwritable.toString()).log();
            return false;
        }
        return JagtJson.write(JagtJson.in(String.valueOf(task.worktreePath()), STORED), review);
    }

    /** Makes the stored verdict answer the hand-back in front; false where there was none or it stayed as it was. */
    public boolean restamp(TaskState task) {
        return stored(task).map(review -> JagtJson.write(JagtJson.in(String.valueOf(task.worktreePath()), STORED),
                review.withRound(task.turnOrNone().id()))).orElse(false);
    }

    /** Whether the reviewer has looked at the task AS IT STANDS, rather than at an earlier hand-back. */
    public boolean readsTheRoundInFront(TaskState task) {
        return of(task).filter(verdict -> verdict.answers(task)).isPresent();
    }

    private static @Nullable Path file(TaskState task) {
        String worktree = task.worktreePath();
        return worktree == null || worktree.isBlank() ? null : Path.of(worktree).resolve(FILE);
    }

    public enum Kind { READY, NOT_READY, QUESTION }

    /** One role's answer. */
    public record Read(String role, Judgement judgement) {
    }

    /**
     * {@code round} names the turn jagt asked about, {@code ownDiff} the task's own diff it read, blank where a
     * role could not read the round.
     */
    @With
    public record Review(long round, String ownDiff, List<Read> reads) {
    }

    /** A finding as the round's reader named it, with the role that made it. */
    public record Said(String role, Finding finding) {

        /** Opens a finding asking the session for evidence. */
        static final String SHOW = "show: ";

        public Identity identity() {
            return new Identity(normal(finding.file()), normal(finding.anchor()), normal(finding.kind()));
        }

        /** A request for evidence decides nothing, so no later round is bound by it. */
        public boolean settles() {
            return !finding.severity().equals("unproven");
        }

        /** Names the file, anchor and kind, so a later reader can name the same finding the same way. */
        public String line() {
            String where = (OneLine.of(finding.file()) + " " + OneLine.of(finding.anchor())).strip();
            String named = String.join(", ", Stream.of(finding.pattern(), finding.kind())
                    .map(OneLine::of).filter(part -> part != null && !part.isEmpty()).toList());
            return "- [" + role + "] " + (where.isEmpty() ? "" : where + " — ")
                    + (settles() ? "" : SHOW) + OneLine.of(finding.issue())
                    + (named.isEmpty() ? "" : " (" + named + ")");
        }

        private static String normal(String part) {
            return part.strip().toLowerCase(Locale.ROOT);
        }
    }

    /** What makes a finding the same one in a later round, however it is worded. */
    public record Identity(String file, String anchor, String kind) {
    }

    /**
     * Ready only where every role found nothing that stops a round; any role's question or failure to read makes it
     * a question, the first of them the one asked.
     */
    public record Verdict(Kind kind, List<Said> findings, List<Said> advice, List<String> questions, long round,
                          String ownDiff) {

        private static final Finding NAMING_NOTHING = new Finding("", "not ready, naming nothing", "", "blocking",
                "", "");

        static Verdict of(Review review) {
            List<Said> findings = new ArrayList<>();
            List<Said> advice = new ArrayList<>();
            List<String> questions = new ArrayList<>();
            review.reads().forEach(read -> sort(read, findings, advice, questions));
            Kind kind = !questions.isEmpty() ? Kind.QUESTION : findings.isEmpty() ? Kind.READY : Kind.NOT_READY;
            return new Verdict(kind, findings, advice, questions, review.round(), review.ownDiff());
        }

        private static void sort(Read read, List<Said> findings, List<Said> advice, List<String> questions) {
            Judgement said = read.judgement();
            if (!said.failure().isBlank()) {
                questions.add("The " + read.role() + " could not read the round: " + OneLine.of(said.failure()));
                return;
            }
            said.findings().forEach(f -> (f.stops() ? findings : advice).add(new Said(read.role(), f)));
            if (said.verdict().equals("question")) {
                questions.add(said.question().isBlank() ? "The " + read.role() + " asks, naming no question"
                        : String.valueOf(OneLine.of(said.question())));
            } else if (!said.verdict().equals("ready") && said.findings().isEmpty()) {
                findings.add(new Said(read.role(), NAMING_NOTHING));
            }
        }

        /** About the turn in front of it, the one jagt asked about, rather than by when it was written. */
        public boolean answers(TaskState task) {
            return round == task.turnOrNone().id();
        }

        public boolean ready() {
            return kind == Kind.READY;
        }

        public String said() {
            return kind.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }

        public String question() {
            return questions.isEmpty() ? "" : questions.getFirst();
        }
    }
}

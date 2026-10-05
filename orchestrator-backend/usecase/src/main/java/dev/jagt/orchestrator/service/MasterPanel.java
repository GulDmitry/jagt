package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Each role the brief names reads the round on its own, in parallel and without the others' reasoning; the
 * verdict is then the code's: a question or an unreadable round goes to the human, any finding back to the
 * session, and ready only where every role found nothing and proved what it rested on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterPanel {

    private final RoundReviewer reviewer;
    private final UsageTracker usage;
    private final MasterBriefs briefs;
    private final MasterDecisions decisions;
    private final RoundFacts facts;

    static final String HUMAN_UNREAD = "what the human typed to the session could not be read";
    /** Opens a finding asking the session for evidence: it decides nothing, so no later round is bound by it. */
    static final String SHOW = "show: ";

    /** What jagt read for a round once, quoted to every role. */
    record RoundRead(String ticket, String diff, String decided, List<String> said, String notes) {
    }

    /** {@code model} blank reads with the Master's own model. */
    public record Role(String name, String question, String model) {

        String modelOr(String masters) {
            return model.isBlank() ? masters : model;
        }
    }

    /** Writes the round's review file; false where the brief could not be read, and nothing was asked. */
    public boolean review(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
        Optional<String> read = briefs.master(taskId, config);
        if (read.isEmpty()) {
            return false;
        }
        String brief = read.get();
        List<Path> worktrees = worktrees(task);
        List<Role> roles = roles(brief, briefs.author());
        String shared = shared(brief, briefs.author(), config.may(MasterRight.ANSWER));
        Optional<List<String>> said = facts.humanSaid(task);
        List<Judgement> judgements = new ArrayList<>();
        if (said.isEmpty()) {
            roles.forEach(role -> judgements.add(Judgement.failed(HUMAN_UNREAD)));
        } else {
            RoundRead quoted = new RoundRead(facts.ticket(taskId), facts.diff(task), decisions.of(task), said.get(),
                    facts.notes(task));
            try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Answer<Judgement>>> asked = roles.stream()
                        .map(role -> new RoundReviewer.Round(shared, prompt(taskId, task, role, quoted), worktrees,
                                role.modelOr(config.modelOrInherited())))
                        .map(round -> threads.submit(() -> reviewer.review(round))).toList();
                for (Future<Answer<Judgement>> answer : asked) {
                    judgements.add(read(taskId, answer));
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        String file = verdictFile(taskId, roles, judgements);
        try {
            Files.writeString(worktrees.getFirst().resolve(MasterReview.FILE), file);
        } catch (IOException unwritable) {
            log.atError().setMessage("master review unwritable").addKeyValue("task", taskId)
                    .addKeyValue("cause", unwritable.toString())
                    .log();
            return false;
        }
        return true;
    }

    /** The session's question decided as the human would; empty where no decision came back. */
    public Optional<String> answer(String taskId, TaskState task, String question,
                                   ConfigService.ConfigFile.MasterConfig config) {
        Optional<String> brief = briefs.master(taskId, config);
        Optional<List<String>> said = facts.humanSaid(task);
        if (brief.isEmpty() || said.isEmpty()) {
            return Optional.empty();
        }
        Answer<Judgement> read = reviewer.review(new RoundReviewer.Round("",
                answerPrompt(taskId, task, brief.get(), briefs.author(), question, decisions.of(task), said.get()),
                worktrees(task),
                config.modelOrInherited()));
        usage.record(AssistantCallKind.MASTER_REVIEW, read.usage());
        usage.chargeTask(taskId, read.usage());
        return read.facts().filter(judged -> judged.failure().isBlank() && !judged.findings().isEmpty())
                .map(judged -> judged.findings().stream().map(f -> oneLine(f.issue())).collect(Collectors.joining("\n")));
    }

    private static List<Path> worktrees(TaskState task) {
        return task.repos().stream().map(TaskRepo::worktreePath).map(Path::of).toList();
    }

    private Judgement read(String taskId, Future<Answer<Judgement>> answer) throws InterruptedException {
        try {
            Answer<Judgement> read = answer.get();
            usage.record(AssistantCallKind.MASTER_REVIEW, read.usage());
            usage.chargeTask(taskId, read.usage());
            return read.facts().orElse(Judgement.failed("the review answered nothing"));
        } catch (ExecutionException thrown) {
            return Judgement.failed("the review threw " + thrown.getCause());
        }
    }

    /** The Master's own table where its brief has one, else the author's: the Master extends the session. */
    static List<Role> roles(String brief, String authorBrief) {
        List<Role> own = table(brief);
        List<Role> roles = own.isEmpty() ? table(authorBrief) : own;
        return roles.isEmpty() ? List.of(new Role("reviewer", "every question your brief asks", "")) : roles;
    }

    private static List<Role> table(String brief) {
        List<Role> roles = new ArrayList<>();
        boolean inTable = false;
        for (String line : brief.lines().map(String::strip).toList()) {
            List<String> cells = line.startsWith("|") ? List.of(line.substring(1).split("\\|")) : List.of();
            if (cells.size() < 2) {
                inTable = false;
            } else if (cells.getFirst().strip().equalsIgnoreCase("role")) {
                inTable = true;
            } else if (inTable && !cells.getFirst().strip().startsWith("-")) {
                roles.add(new Role(cells.get(0).strip(), cells.get(1).strip(),
                        cells.size() > 2 ? cells.get(2).strip() : ""));
            }
        }
        return roles;
    }

    static String verdictFile(String taskId, List<Role> roles, List<Judgement> judgements) {
        List<String> lines = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        List<String> advice = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            String role = roles.get(i).name();
            Judgement said = judgements.get(i);
            if (!said.failure().isBlank()) {
                questions.add("The " + role + " could not read the round: " + oneLine(said.failure()));
                continue;
            }
            // Only what breaks something or misses the ticket stops a round; the rest is advice, a `#` line jagt skips.
            said.findings().forEach(f -> (f.stops() ? lines : advice).add((f.stops() ? "- [" : "# advice [") + role
                    + "] " + oneLine(f.file()) + " — " + (f.severity().equals("unproven") ? SHOW : "")
                    + oneLine(f.issue())
                    + (f.pattern().isBlank() ? "" : " (" + oneLine(f.pattern()) + ")")));
            said.premises().stream().filter(p -> p.provenBy().isBlank())
                    .forEach(p -> advice.add("# unproven [" + role + "] " + oneLine(p.claim())));
            if (said.verdict().equals("question")) {
                questions.add(said.question().isBlank() ? "The " + role + " asks, naming no question"
                        : oneLine(said.question()));
            } else if (!said.verdict().equals("ready") && said.findings().isEmpty()) {
                lines.add("- [" + role + "] not ready, naming nothing");
            }
        }
        StringBuilder file = new StringBuilder("# " + taskId + " — master review\n\n");
        advice.forEach(line -> file.append(line).append('\n'));
        lines.forEach(line -> file.append(line).append('\n'));
        if (!questions.isEmpty()) {
            questions.stream().skip(1).forEach(q -> file.append("- also asked: ").append(q).append('\n'));
            return file.append(questions.getFirst()).append("\n\nVERDICT: question\n").toString();
        }
        if (!lines.isEmpty()) {
            return file.append("\nVERDICT: not ready\n").toString();
        }
        return file.append("nothing wrong\n\nVERDICT: ready\n").toString();
    }

    /** The file is read line by line: a finding that wraps would count twice. */
    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    static String answerPrompt(String taskId, TaskState task, String brief, String authorBrief, String question,
                               String decided, List<String> said) {
        return "You stand in for the human on task " + taskId + ". The session working it stopped to ask: "
                + question + "\n\nThe brief you judge by:\n" + brief + "\n\n"
                + "The brief the session works to, its %s filled per task:\n" + authorBrief + "\n\n"
                + round(taskId, task) + settled(decided) + humanSaid(said)
                + "Read the ticket and the code, then decide as the human would, by both briefs and the codebase."
                + " Decide only what you can prove; where the decision rests on a fact only the session can show,"
                + " the decision is to show it first: name exactly what, and what you decide on each answer."
                + " Never answer question and never defer: the decision is the answer. verdict: ready. findings:"
                + " the decision, one per line — what the session does, and the one clause of why. premises: what"
                + " it rests on, each with provenBy — the file:line or read-only command that shows it. failure:"
                + " blank unless something stopped you reading, then what.";
    }

    private static String settled(String decided) {
        return decided.isBlank() ? "" : "Settled in earlier rounds, and binding unless the human's own words below"
                + " say otherwise or the session proves one wrong: reopen one only for a blocking reason or that"
                + " proof.\n" + decided + "\n";
    }

    private static String humanSaid(List<String> said) {
        return said.isEmpty() ? "" : "What the human typed to the session, oldest first. Their word stands over"
                + " anything decided in their stead:\n<human_said>\n" + String.join("\n", said) + "\n</human_said>\n";
    }

    private static String round(String taskId, TaskState task) {
        String ticket = task.ticketUrl() == null || task.ticketUrl().isBlank() ? "none" : task.ticketUrl();
        return "The task: " + taskId + ", worktrees " + task.repos().stream().map(TaskRepo::worktreePath).toList()
                + ", base " + task.baseBranchOr("the base branch") + ", ticket " + ticket + ". Run no build and no"
                + " test.\n";
    }

    /** Names no role and no task, so every reader of every round sends it alike and finds it cached. */
    static String shared(String brief, String authorBrief, boolean decides) {
        return "The brief you judge by:\n" + brief + "\n\n"
                + "The brief the author worked to, its %s filled per task; yours extends it:\n"
                + authorBrief + "\n\n"
                + "Read the ticket, quoted in the round where jagt read it and with your tracker tools where it is"
                + " not, then everything the task changed against its base, committed and not: quoted in the round"
                + " where jagt read it, with git where it is not. Judge from the"
                + " ticket and the diff: the author's own account is not evidence, what it points at is. A"
                + " `disputed:` line in the session's notes names a ticket line, a file:line or a command and what it"
                + " printed: check it, and proven, it stands over your own earlier finding. Rule only on what you can"
                + " prove from what you read; a decision resting on a fact you cannot read is unproven, never a"
                + " guess. Run no build and no test: they ran before the round reached you.\n"
                + "The best is the enemy of the good: ready means nothing is broken and nothing misses the ticket.\n"
                + "verdict: ready, not ready, or question. findings: one per problem — file, what is wrong and the"
                + " one clause of why, pattern: two to four words naming the kind of problem, and severity:"
                + " blocking (someone relying on it today breaks), wrong (not what the ticket asks), unguarded"
                + " (right, but nothing fails when it breaks), unproven (you would decide it, but nothing you can"
                + " read proves it: the issue names exactly what the session must show) or noise (style or taste);"
                + " only blocking, wrong and unproven stop the round. question: only with verdict question, the one thing the human must decide."
                + (decides ? " You stand in for the human: never answer question. Where the ticket or the code"
                        + " leaves something open, decide it as they would, by both briefs and the codebase, and"
                        + " write the decision as a finding." : "")
                + " premises: every claim the verdict rests on, each with provenBy — the file:line, or the read-only"
                + " command and what it printed, that shows it; blank where you only reasoned it. failure: blank"
                + " unless something stopped you reading the round, then what.";
    }

    /** The round before the role, so every role after the first finds it cached. */
    static String prompt(String taskId, TaskState task, Role role, RoundRead read) {
        String link = task.ticketUrl() == null || task.ticketUrl().isBlank() ? "none" : task.ticketUrl();
        return "The round: task " + taskId + ", worktrees " + task.repos().stream().map(TaskRepo::worktreePath)
                        .toList() + ", base " + task.baseBranchOr("the base branch") + ", ticket " + link + ".\n"
                + (read.ticket().isBlank() ? "" : "The ticket, read for this round:\n<ticket>\n" + read.ticket()
                        + "\n</ticket>\n")
                + (read.diff().isBlank() ? "" : "The diff, read for this round:\n<diff>\n" + read.diff() + "</diff>\n")
                + (read.notes().isBlank() ? "" : "The session's notes:\n<session_notes>\n" + read.notes()
                        + "\n</session_notes>\n")
                + settled(read.decided()) + humanSaid(read.said())
                + "\nYou are the " + role.name() + " of jagt's unattended reviewer, and only that role: "
                + role.question() + ". The other roles read this round separately; say nothing outside yours.\n";
    }
}

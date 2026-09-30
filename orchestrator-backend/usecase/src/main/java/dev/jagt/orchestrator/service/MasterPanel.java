package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.task.AssistantCallKind;
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
    private final OrchestratorPaths paths;
    private final PromptTemplates prompts;

    public record Role(String name, String question) {
    }

    /** Writes the round's review file; false where the brief could not be read, and nothing was asked. */
    public boolean review(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
        String brief;
        try {
            brief = Files.readString(paths.root().resolve(config.briefOrDefault()));
        } catch (IOException unreadable) {
            log.atError().setMessage("master brief unreadable").addKeyValue("task", taskId)
                    .addKeyValue("file", config.briefOrDefault())
                    .addKeyValue("cause", unreadable.toString())
                    .log();
            return false;
        }
        List<Path> worktrees = task.repos().stream().map(TaskRepo::worktreePath).map(Path::of).toList();
        List<Role> roles = roles(brief, prompts.subAgentContext());
        List<Judgement> judgements = new ArrayList<>();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Answer<Judgement>>> asked = roles.stream()
                    .map(role -> new RoundReviewer.Round(prompt(taskId, task, brief, prompts.subAgentContext(), role), worktrees,
                            config.modelOrInherited()))
                    .map(round -> threads.submit(() -> reviewer.review(round))).toList();
            for (Future<Answer<Judgement>> answer : asked) {
                judgements.add(read(taskId, answer));
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
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
        return roles.isEmpty() ? List.of(new Role("reviewer", "every question your brief asks")) : roles;
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
                roles.add(new Role(cells.get(0).strip(), cells.get(1).strip()));
            }
        }
        return roles;
    }

    static String verdictFile(String taskId, List<Role> roles, List<Judgement> judgements) {
        List<String> lines = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            String role = roles.get(i).name();
            Judgement said = judgements.get(i);
            if (!said.failure().isBlank()) {
                questions.add("The " + role + " could not read the round: " + oneLine(said.failure()));
                continue;
            }
            said.findings().forEach(f -> lines.add("- [" + role + "] " + oneLine(f.file()) + " — "
                    + oneLine(f.issue()) + (f.pattern().isBlank() ? "" : " (" + oneLine(f.pattern()) + ")")));
            said.premises().stream().filter(p -> p.provenBy().isBlank())
                    .forEach(p -> lines.add("- [" + role + "] unproven: " + oneLine(p.claim())));
            if (said.verdict().equals("question")) {
                questions.add(said.question().isBlank() ? "The " + role + " asks, naming no question"
                        : oneLine(said.question()));
            } else if (!said.verdict().equals("ready") && said.findings().isEmpty()) {
                lines.add("- [" + role + "] not ready, naming nothing");
            }
        }
        StringBuilder file = new StringBuilder("# " + taskId + " — master review\n\n");
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

    static String prompt(String taskId, TaskState task, String brief, String authorBrief, Role role) {
        String ticket = task.ticketUrl() == null || task.ticketUrl().isBlank() ? "none" : task.ticketUrl();
        return "You are the " + role.name() + " of jagt's unattended reviewer, and only that role: "
                + role.question() + ". The other roles read this round separately; say nothing outside yours.\n\n"
                + "The brief you judge by:\n" + brief + "\n\n"
                + "The brief the author worked to, its %s filled per task; yours extends it:\n"
                + authorBrief + "\n\n"
                + "The round: task " + taskId + ", worktrees " + task.repos().stream().map(TaskRepo::worktreePath)
                        .toList() + ", base " + task.baseBranchOr("the base branch") + ", ticket " + ticket + ".\n"
                + "Read the ticket with your tracker tools, then everything the task changed against its base,"
                + " committed and not. Judge from the ticket and the diff: the author's own account is not"
                + " evidence. Run no build and no test: they ran before the round reached you.\n"
                + "verdict: ready, not ready, or question. findings: one per problem — file, what is wrong and the"
                + " one clause of why, and pattern: two to four words naming the kind of problem. question: only"
                + " with verdict question, the one thing the human must decide. premises: every claim the verdict"
                + " rests on, each with provenBy — the file:line, or the read-only command and what it printed, that"
                + " shows it; blank where you only reasoned it. failure: blank unless something stopped you reading the round, then what.";
    }
}

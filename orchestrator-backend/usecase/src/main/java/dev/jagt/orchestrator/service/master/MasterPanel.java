package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OneLine;
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

    private final ChargedReviews reviews;
    private final MasterBriefs briefs;
    private final RoundQuotes quotes;

    static final String HUMAN_UNREAD = "what the human typed to the session could not be read";
    /** Opens a finding asking the session for evidence: it decides nothing, so no later round is bound by it. */
    public static final String SHOW = "show: ";

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
        Optional<String> masters = briefs.master(taskId, config);
        if (masters.isEmpty()) {
            return false;
        }
        String brief = masters.get();
        List<Path> worktrees = worktrees(task);
        List<Role> roles = roles(brief, briefs.author());
        String shared = shared(brief, briefs.author(), config.may(MasterRight.ANSWER),
                reviews.loadsMcpServer());
        Optional<RoundRead> read = quotes.round(taskId, task);
        List<Judgement> judgements = new ArrayList<>();
        if (read.isEmpty()) {
            roles.forEach(role -> judgements.add(Judgement.failed(HUMAN_UNREAD)));
        } else {
            RoundRead quoted = read.get();
            try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Judgement>> asked = roles.stream()
                        .map(role -> new RoundReviewer.Round(shared, prompt(taskId, task, role, quoted), worktrees,
                                role.modelOr(config.modelOrInherited())))
                        .map(round -> threads.submit(() -> reviews.review(taskId, round))).toList();
                for (Future<Judgement> answer : asked) {
                    judgements.add(read(answer));
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return write(taskId, worktrees, verdictFile(taskId, roles, judgements));
    }

    /** One read, not one per role: a plan answers a single question, whether it does what the ticket asks. */
    public boolean plan(String taskId, TaskState task, ConfigService.ConfigFile.MasterConfig config) {
        Optional<String> brief = briefs.master(taskId, config);
        if (brief.isEmpty()) {
            return false;
        }
        Optional<RoundRead> read = quotes.plan(taskId, task);
        Judgement judged = read.isEmpty() ? Judgement.failed(HUMAN_UNREAD)
                : reviews.review(taskId, new RoundReviewer.Round("", planPrompt(taskId, task, brief.get(),
                        quotes.planFile(task), read.get(), config.may(MasterRight.ANSWER),
                        reviews.loadsMcpServer()),
                        worktrees(task), config.modelOrInherited()));
        return write(taskId, worktrees(task), verdictFile(taskId, List.of(PLANNER), List.of(judged)));
    }

    private static final Role PLANNER = new Role("planner", "whether the plan does what the ticket asks", "");

    private static boolean write(String taskId, List<Path> worktrees, String file) {
        try {
            Files.writeString(worktrees.getFirst().resolve(MasterReview.FILE), file);
            return true;
        } catch (IOException unwritable) {
            log.atError().setMessage("master review unwritable").addKeyValue("task", taskId)
                    .addKeyValue("cause", unwritable.toString())
                    .log();
            return false;
        }
    }

    /** The session's question decided as the human would; empty where no decision came back. */
    public Optional<String> answer(String taskId, TaskState task, String question,
                                   ConfigService.ConfigFile.MasterConfig config, boolean stuck) {
        Optional<String> brief = briefs.master(taskId, config);
        Optional<RoundRead> read = quotes.question(task);
        if (brief.isEmpty() || read.isEmpty()) {
            return Optional.empty();
        }
        Judgement judged = reviews.review(taskId, new RoundReviewer.Round("",
                answerPrompt(taskId, task, brief.get(), briefs.author(), question, read.get().decided(),
                        read.get().said(), stuck, reviews.loadsMcpServer()),
                worktrees(task),
                config.modelOrInherited()));
        return Optional.of(judged).filter(said -> said.failure().isBlank() && !said.findings().isEmpty())
                .map(said -> said.findings().stream().map(f -> OneLine.of(f.issue())).collect(Collectors.joining("\n")));
    }

    private static List<Path> worktrees(TaskState task) {
        return task.repos().stream().map(TaskRepo::worktreePath).map(Path::of).toList();
    }

    private static Judgement read(Future<Judgement> answer) throws InterruptedException {
        try {
            return answer.get();
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
                questions.add("The " + role + " could not read the round: " + OneLine.of(said.failure()));
                continue;
            }
            said.findings().forEach(f -> (f.stops() ? lines : advice).add((f.stops() ? "- [" : "# advice [") + role
                    + "] " + OneLine.of(f.file()) + " — " + (f.severity().equals("unproven") ? SHOW : "")
                    + OneLine.of(f.issue())
                    + (f.pattern().isBlank() ? "" : " (" + OneLine.of(f.pattern()) + ")")));
            said.premises().stream().filter(p -> p.provenBy().isBlank())
                    .forEach(p -> advice.add("# unproven [" + role + "] " + OneLine.of(p.claim())));
            if (said.verdict().equals("question")) {
                questions.add(said.question().isBlank() ? "The " + role + " asks, naming no question"
                        : OneLine.of(said.question()));
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

    static String answerPrompt(String taskId, TaskState task, String brief, String authorBrief, String question,
                               String decided, List<String> said, boolean stuck, boolean tracker) {
        return GOAL + "You stand in for the human on task " + taskId + ". The session working it stopped to ask: "
                + question + "\n\nThe brief you judge by:\n" + brief + "\n\n"
                + "The brief the session works to, its %s filled per task:\n" + authorBrief + "\n\n"
                + round(taskId, task) + settled(decided) + humanSaid(said) + (stuck ? STUCK : "")
                + ticket(tracker, "code") + " Read the code, its history with " + GIT + "."
                + " Then decide as the human would, by both briefs and the codebase."
                + " Decide only what you can prove."
                + " Where the decision rests on a fact only the session can show, the decision is to show it first."
                + " Name exactly what to show, and what you decide on each answer."
                + " A check this task's request turns red is the task's to turn green, code that predates it included."
                + " This holds whatever the ticket's scope: an override, an exception or a human's action is no answer"
                + " while a change in the worktrees can pass it. Decide that change."
                + " Work needing a branch other than " + taskId + " is a task of its own."
                + " Open it with a finding that is exactly `do " + task.project() + " <what to do…> from <branch>`."
                + " jagt runs that line as the human's own."
                + " Merging into the deploy branch is never such a task: it is this task's own `deploy`."
                + " jagt presses it once the request is green with every thread closed; until then the session waits."
                + " Never answer question and never defer: the decision is the answer."
                + " verdict: ready."
                + " findings: the decision, one per line — what the session does, and the one clause of why."
                + " premises: what it rests on, each with provenBy — the file:line or read-only command"
                + " that shows it."
                + " failure: blank unless something stopped you reading, then what.";
    }

    static String planPrompt(String taskId, TaskState task, String brief, String plan, RoundRead read,
                             boolean decides, boolean tracker) {
        return GOAL + "Task " + taskId + " stopped at its plan, before any code. The brief you judge by:\n" + brief
                + "\n\n" + round(taskId, task)
                + (!read.ticket().isBlank() ? "The ticket:\n<ticket>\n" + read.ticket() + "\n</ticket>\n"
                        : ticket(tracker, "plan") + "\n")
                + (plan.isBlank() ? "" : "The plan, plan.md:\n<plan>\n" + plan + "\n</plan>\n")
                + (read.notes().isBlank() ? "" : "The session's notes:\n<session_notes>\n" + read.notes()
                        + "\n</session_notes>\n")
                + settled(read.decided()) + humanSaid(read.said())
                + "Judge one thing: whether this plan does what the ticket asks, by your brief and the codebase."
                + " A plan missing a ticket line is wrong. So is one doing what no ticket line asks."
                + " Read the code only where the plan rests on it, its history with " + GIT + "."
                + " A `disputed:` line in the notes names its evidence: check it, and proven, it stands."
                + (plan.isBlank() ? " The session wrote no plan.md: that alone is not ready." : "")
                + " verdict: ready where the plan holds, not ready, or question."
                + " findings: one per problem — what the plan gets wrong, and the one clause of why."
                + " Each finding carries pattern: two to four words naming the kind of problem."
                + " Each finding carries severity: wrong, blocking, unproven or noise. Only noise lets the plan pass."
                + " question: only with verdict question, the one thing the human must decide."
                + (decides ? " You stand in for the human: never answer question. Decide what the ticket leaves"
                        + " open as they would, and write the decision as a finding." : "")
                + " premises: every claim the verdict rests on, each with provenBy — the file:line, or the read-only"
                + " command and what it printed. failure: blank unless something stopped you reading, then what.";
    }

    /** The Master's reason to exist, ahead of every read so no rule below it is taken for the goal. */
    static final String GOAL = "Your goal is the task finished: ready to merge, its request's checks green, the ticket"
            + " met, the codebase's architecture and naming kept. You are the strongest reader in this loop."
            + " Where the session stalls or a check stays red, find the cause. Decide the change that clears it, and"
            + " insist on it until it holds. Never hold the task, defer it or settle for red.\n\n";

    private static final String GIT = "git in plain words (no quote, ~, ^, brace or glob; diff, log, show and blame"
            + " carry `--no-ext-diff --no-textconv`)";

    /** Where no MCP server loads, no ticket the round does not quote was read. */
    private static String ticket(boolean tracker, String judged) {
        return tracker ? "Read the ticket with your MCP tools." : "The ticket was not read: rule only on the " + judged
                + ", and call any premise resting on the ticket unproven.";
    }

    private static final String STUCK = "Your last answers over this tree changed nothing in it: the session"
            + " could act on none, and it restarts with fresh tools. Decide the change in the worktrees that brings"
            + " the task to ready-to-merge; where options remain, recommend the best and take it.\n";

    private static String settled(String decided) {
        return decided.isBlank() ? "" : "Settled in earlier rounds, and binding unless the human's own words below"
                + " say otherwise or the session proves one wrong: reopen one only for a blocking reason or that"
                + " proof. A decision after which the task did not move is no settlement.\n" + decided + "\n";
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
    static String shared(String brief, String authorBrief, boolean decides, boolean tracker) {
        return GOAL + "The brief you judge by:\n" + brief + "\n\n"
                + "The brief the author worked to, its %s filled per task; yours extends it:\n"
                + authorBrief + "\n\n"
                + "The ticket is quoted in the round where jagt read it. Elsewhere: " + ticket(tracker, "diff")
                + " Then read everything the task changed against its base, committed and not: quoted in the round"
                + " where jagt read it, with " + GIT + " where it is not; `git -C <worktree>` reads each worktree"
                + " past the first. Judge from the"
                + " ticket and the diff: the author's own account is not evidence, what it points at is. A"
                + " `disputed:` line in the session's notes names a ticket line, a file:line or a command and what it"
                + " printed: check it, and proven, it stands over your own earlier finding. Rule only on what you can"
                + " prove from what you read; a decision resting on a fact you cannot read is unproven, never a"
                + " guess. Run no build and no test: they ran before the round reached you.\n"
                + "The best is the enemy of the good: ready means nothing is broken and nothing misses the ticket.\n"
                + "verdict: ready, not ready, or question."
                + " findings: one per problem — file, what is wrong and the one clause of why."
                + " Each finding carries pattern: two to four words naming the kind of problem."
                + " Each finding carries severity, one of five."
                + " blocking: someone relying on it today breaks."
                + " wrong: not what the ticket asks."
                + " unguarded: right, but nothing fails when it breaks."
                + " unproven: you would decide it, but nothing you can read proves it."
                + " An unproven issue names exactly what the session must show."
                + " noise: style or taste."
                + " Only blocking, wrong and unproven stop the round."
                + " question: only with verdict question, the one thing the human must decide."
                + (decides ? " You stand in for the human: never answer question. Where the ticket or the code"
                        + " leaves something open, decide it as they would, by both briefs and the codebase."
                        + " Write the decision as a finding." : "")
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

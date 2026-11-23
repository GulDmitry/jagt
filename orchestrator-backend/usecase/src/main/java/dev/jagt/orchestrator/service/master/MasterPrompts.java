package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.service.master.MasterPanel.Asked;
import dev.jagt.orchestrator.service.master.MasterPanel.Briefs;
import dev.jagt.orchestrator.service.master.MasterPanel.Role;
import dev.jagt.orchestrator.service.master.MasterPanel.RoundRead;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.text.Outside;
import java.util.List;
import java.util.stream.Collectors;

/** The words each master read is asked in. */
final class MasterPrompts {

  private MasterPrompts() {}

  static String answerPrompt(
      String taskId, TaskState task, Briefs standards, Asked asked, boolean tracker) {
    return GOAL
        + "<role>You stand in for the human on task "
        + taskId
        + ".</role>\n"
        + RoundReviewer.QUESTION_LEAD
        + "\n"
        + "<question>\n"
        + asked.question()
        + "\n</question>\n"
        + "The brief you judge by:\n<brief>\n"
        + standards.judgeBy()
        + "\n</brief>\n"
        + "The brief the session works to, its %s filled per task:\n<author_brief>\n"
        + standards.worksTo()
        + "\n</author_brief>\n"
        + round(taskId, task)
        + settled(asked.decided())
        + humanSaid(asked.said())
        + (asked.stuck() ? STUCK : "")
        + "<rules>\n"
        + "1. "
        + ticket(tracker, "code")
        + " Read the code, its history with "
        + GIT
        + ".\n"
        + "2. Then decide as the human would, by both briefs and the codebase. Decide only what you"
        + " can prove.\n"
        + "3. Where the decision rests on a fact only the session can show, the decision is to show"
        + " it first. Name exactly what to show, and what you decide on each answer.\n"
        + "4. A check this task's request turns red is the task's to turn green, code that predates"
        + " it included. This holds whatever the ticket's scope: an override, an exception or a"
        + " human's action is no answer while a change in the worktrees can pass it. Decide that"
        + " change.\n"
        + "5. Work "
        + taskId
        + " needs is done in "
        + taskId
        + ", on its own branch in the repository it lies in. Never decide another task or branch:"
        + " only the human opens one. A repository the task does not hold is never yours to decide:"
        + " the session asks with `needsProject`, jagt attaches it.\n"
        + "6. Merging into the deploy branch is never such a task: it is this task's own `deploy`."
        + " jagt presses it once the request is green with every thread closed; until then the"
        + " session waits.\n"
        + "7. Never answer question and never defer: the decision is the answer.\n"
        + "</rules>\n"
        + "<output_format>\n"
        + "- verdict: ready.\n"
        + "- findings: the decision, one per line — what the session does, and the one clause of"
        + " why.\n"
        + "- newTask: blank, unless the decision is work outside this task: that work in one line."
        + " jagt opens no task.\n"
        + "- premises: what it rests on, each with provenBy — the file:line or read-only command"
        + " that shows it.\n"
        + "- failure: blank unless something stopped you reading, then what.\n"
        + "</output_format>\n"
        + RESPOND;
  }

  static String planPrompt(
      String taskId,
      TaskState task,
      String brief,
      String plan,
      RoundRead read,
      boolean decides,
      boolean tracker) {
    return GOAL
        + "<role>You judge the plan of task "
        + taskId
        + ", which stopped before any code.</role>\n"
        + "The brief you judge by:\n<brief>\n"
        + brief
        + "\n</brief>\n"
        + round(taskId, task)
        + (!read.ticket().isBlank()
            ? "The ticket:\n" + Outside.fence("ticket", read.ticket()) + "\n"
            : ticket(tracker, "plan") + "\n")
        + (plan.isBlank() ? "" : "The plan, plan.md:\n<plan>\n" + plan + "\n</plan>\n")
        + (task.gate().probes().isEmpty()
            ? ""
            : "The acceptance probes jagt read from it; once it holds, jagt runs them itself before"
                + " any round reaches review, and a red one returns the round:\n"
                + "<probes>\n"
                + task.gate().probes().stream()
                    .map(Probe::toString)
                    .collect(Collectors.joining("\n"))
                + "\n</probes>\n")
        + (read.notes().isBlank()
            ? ""
            : "The session's notes:\n<session_notes>\n" + read.notes() + "\n</session_notes>\n")
        + settled(read.decided())
        + humanSaid(read.said())
        + "<rules>\n"
        + "- Judge one thing: whether this plan does what the ticket asks, by your brief and the"
        + " codebase.\n"
        + "- A plan missing a ticket line is wrong. So is one doing what no ticket line asks.\n"
        + "- Read the code only where the plan rests on it, its history with "
        + GIT
        + ".\n"
        + "- A `disputed:` line in the notes names its evidence: check it, and proven, it stands.\n"
        + (plan.isBlank() ? "- The session wrote no plan.md: that alone is not ready.\n" : "")
        + (decides
            ? "- You stand in for the human: never answer question. Decide what the ticket leaves"
                + " open as they would, and write the decision as a finding.\n"
            : "")
        + "</rules>\n"
        + "<output_format>\n"
        + "- verdict: ready where the plan holds, not ready, or question.\n"
        + "- findings: one per problem — what the plan gets wrong, and the one clause of why."
        + IDENTITY
        + SEVERITY
        + "- question: only with verdict question, the one thing the human must decide.\n"
        + "- premises: every claim the verdict rests on, each with provenBy — the file:line, or the"
        + " read-only command and what it printed.\n"
        + "- failure: blank unless something stopped you reading, then what.\n"
        + "</output_format>\n"
        + RESPOND;
  }

  private static final String RESPOND = "Respond directly, no preamble.";

  private static final String IDENTITY =
      " Each finding carries pattern: two to four words naming the kind of problem; anchor: the"
          + " symbol, heading or key it sits on, as written in the file, blank for the whole file;"
          + " kind: behaviour (it does the wrong thing), missing (a ticket line not done), scope"
          + " (past the ticket), test (a check missing or wrong) or structure (architecture,"
          + " naming, duplication). A problem an earlier round named keeps its file, anchor and"
          + " kind.\n";

  private static final String SEVERITY =
      "- severity, one of five:\n"
          + "  - blocking: someone relying on it today breaks.\n"
          + "  - wrong: not what the ticket asks.\n"
          + "  - unguarded: right, but nothing fails when it breaks.\n"
          + "  - unproven: you would decide it, but nothing you can read proves it. An unproven"
          + " issue names exactly what the session must show.\n"
          + "  - noise: style or taste.\n"
          + "  Only blocking, wrong and unproven stop the round, and only for correctness or a"
          + " stated requirement: wording, glossary and style are noise.\n";

  /**
   * The Master's reason to exist, ahead of every read so no rule below it is taken for the goal.
   */
  static final String GOAL =
      "<goal>\n"
          + "Your goal is the task finished: ready to merge, its request's checks green, the ticket"
          + " met, the codebase's architecture and naming kept. You are the strongest reader in"
          + " this loop. Where the session stalls or a check stays red, find the cause. Decide the"
          + " change that clears it, and insist on it until it holds. Never hold the task, defer it"
          + " or settle for red.\n"
          + "</goal>\n";

  private static final String GIT =
      "git in plain words, one command per call (no `&&`, `;` or pipe, no quote, ~, ^, brace or"
          + " glob; diff, log, show and blame carry `--no-ext-diff --no-textconv`;"
          + " `git -C <worktree>` reads each worktree past the first)";

  /** Where no MCP server loads, no ticket the round does not quote was read. */
  private static String ticket(boolean tracker, String judged) {
    return tracker
        ? "Read the ticket with your MCP tools."
        : "The ticket was not read: rule only on the "
            + judged
            + ", and call any premise resting on the ticket unproven.";
  }

  private static final String STUCK =
      "Your last answers over this tree changed nothing in it: the session could act on none, and"
          + " it restarts with fresh tools. Decide the change in the worktrees that brings the task"
          + " to ready-to-merge; where options remain, recommend the best and take it.\n";

  private static String settled(String decided) {
    return decided.isBlank()
        ? ""
        : "Settled in earlier rounds, and binding unless the human's own words below say otherwise"
            + " or the session proves one wrong: reopen one only for a blocking reason or that"
            + " proof. A decision after which the task did not move is no settlement.\n"
            + decided
            + "\n";
  }

  private static String humanSaid(List<String> said) {
    return said.isEmpty()
        ? ""
        : "What the human typed to the session, oldest first. Their word stands over"
            + " anything decided in their stead:\n<human_said>\n"
            + String.join("\n", said)
            + "\n</human_said>\n";
  }

  private static String round(String taskId, TaskState task) {
    String ticket =
        task.ticket().ticketUrl() == null || task.ticket().ticketUrl().isBlank()
            ? "none"
            : task.ticket().ticketUrl();
    return "The task: "
        + taskId
        + ", worktrees "
        + task.repos().stream().map(TaskRepo::worktreePath).toList()
        + ", base "
        + task.ticket().baseBranchOr("the base branch")
        + ", ticket "
        + ticket
        + ". Run no build and no"
        + " test.\n";
  }

  /** Names no role and no task. */
  static String shared(String brief, String authorBrief, boolean decides, boolean tracker) {
    return GOAL
        + "The brief you judge by:\n<brief>\n"
        + brief
        + "\n"
        + "</brief>\n"
        + "The brief the author worked to, its %s filled per task; yours extends it:\n"
        + "<author_brief>\n"
        + authorBrief
        + "\n</author_brief>\n"
        + "<rules>\n"
        + "1. The ticket is quoted in the round where jagt read it. Elsewhere: "
        + ticket(tracker, "diff")
        + "\n"
        + "2. Then read everything the task changed against its base, committed and not: quoted in"
        + " the round where jagt read it, with "
        + GIT
        + " where it is not.\n"
        + "3. Judge from the ticket and the diff: the author's own account is not evidence, what it"
        + " points at is.\n"
        + "4. A `disputed:` line in the session's notes names a ticket line, a file:line or a"
        + " command and what it printed: check it, and proven, it stands over your own earlier"
        + " finding.\n"
        + "5. Rule only on what you can prove from what you read; a decision resting on a fact you"
        + " cannot read is unproven, never a guess.\n"
        + "6. Run no build and no test: they ran before the round reached you.\n"
        + "7. The best is the enemy of the good: ready means nothing is broken and nothing misses"
        + " the ticket.\n"
        + (decides
            ? "8. You stand in for the human: never answer question. Where the ticket or the code"
                + " leaves something open, decide it as they would, by both briefs and the"
                + " codebase. Write the decision as a finding.\n"
            : "")
        + "</rules>\n"
        + "<output_format>\n"
        + "- verdict: ready, not ready, or question.\n"
        + "- findings: one per problem — file, what is wrong and the one clause of why."
        + IDENTITY
        + SEVERITY
        + "- question: only with verdict question, the one thing the human must decide.\n"
        + "- premises: every claim the verdict rests on, each with provenBy — the file:line, or the"
        + " read-only command and what it printed, that shows it; blank where you only reasoned"
        + " it.\n"
        + "- failure: blank unless something stopped you reading the round, then what.\n"
        + "</output_format>";
  }

  /** What every role of one round reads alike, so the roles after the first find it cached. */
  static String quote(String taskId, TaskState task, RoundRead read) {
    String link =
        task.ticket().ticketUrl() == null || task.ticket().ticketUrl().isBlank()
            ? "none"
            : task.ticket().ticketUrl();
    return "The round: task "
        + taskId
        + ", worktrees "
        + task.repos().stream().map(TaskRepo::worktreePath).toList()
        + ", base "
        + task.ticket().baseBranchOr("the base branch")
        + ", ticket "
        + link
        + ".\n"
        + (read.ticket().isBlank()
            ? ""
            : "The ticket, read for this round:\n" + Outside.fence("ticket", read.ticket()) + "\n")
        + (read.diff().isBlank()
            ? ""
            : "The diff, read for this round:\n<diff>\n" + read.diff() + "</diff>\n")
        + (read.notes().isBlank()
            ? ""
            : "The session's notes:\n<session_notes>\n" + read.notes() + "\n</session_notes>\n")
        + (task.gate().probes().isEmpty()
            ? ""
            : "The acceptance probes of the ratified plan, as jagt itself ran"
                + " them, facts the session cannot author:\n<probes>\n"
                + task.gate().probes().stream().map(Probe::result).collect(Collectors.joining("\n"))
                + "\n</probes>\n")
        + settled(read.decided())
        + humanSaid(read.said());
  }

  static String prompt(Role role) {
    return "<role>You are the "
        + role.name()
        + " of jagt's unattended reviewer, and only that role: "
        + role.question()
        + ". The other roles read this round separately; say nothing outside yours.</role>\n"
        + RESPOND;
  }
}

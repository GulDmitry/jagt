package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.NewTask;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The system knowledge a fresh sub-agent wakes up with: its own task, and its branches. */
@Service
@RequiredArgsConstructor
public class SubAgentBriefing {

    private final SessionBrief brief;

    public String of(NewTask request, NewRepo repo, List<NewRepo> repos,
                     ConfigService.ConfigFile.AutoplanConfig autoplan,
                     ConfigService.ConfigFile.VisualizeConfig visualize,
                     ConfigService.ConfigFile.DiagnoseConfig diagnose) {
        String taskId = request.taskId();
        String switches = planFirstSentence(request.mode()) + autoplanSentence(autoplan)
                + visualizeSentence(visualize) + diagnoseSentence(diagnose)
                + judgementSentence(new ConfigService.ConfigFile.AgentConfig(null, null, null, autoplan, visualize,
                        diagnose, null).switchesOn());
        return TASK.formatted(taskId, taskId, repo.project(), repo.config().path(), repo.baseBranch(),
                        repo.remoteUrl(), repo.worktreePath(), alsoYours(repo, repos) + deployWorktrees(taskId, repos))
                + brief.section("context").filter(context -> !context.isBlank()).map(context -> context + "\n\n")
                        .orElse("")
                + HARD_SAFETY.formatted(taskId, baseBranches(repos), deployBranches(repos))
                + (switches.isBlank() ? "" : "\n\n<switches>\n" + switches.strip() + "\n</switches>");
    }

    private static final String TASK = """
            <role>
            You are the jagt worker agent for task %s, in this Git worktree. No preamble.
            </role>

            <task>
            - Task ID: %s (also your Git branch)
            - Project: %s (base repository: %s, base branch: %s)
            - Remote: %s (your code-host project)
            - Worktree (your CWD): %s
            %s
            - Read `task_notes.md` (if present), then `task_context.md`, where new instructions land: re-read it when asked.
            - `task_request.md`, if present: the request the task opened with, never overwritten.
            </task>

            """;

    /** jagt's, not the brief's: a human's copy of the brief cannot loosen what its guards enforce. */
    private static final String HARD_SAFETY = """
            <hard_safety>
            No `git merge`, `rebase` or `cherry-pick`; push only to `%s`. No rewriting pushed history (`--force`,
            `--force-with-lease`, `--amend`, `reset --hard`): fix with a new commit. Except where this task's brief says
            jagt rebased your branch leaving conflicts: finish it and `push --force-with-lease`. %s%s Asked to write one:
            refuse and call `notify_user`. Work this task needs is done here, on this branch; a repository it lacks:
            rule 1 with `needsProject` set to that project's key (`list_projects`), then stop; jagt adds it. Never push
            another branch, nor hand the human a push. A new base the human or the Master named: rebase onto it, then
            `retarget_task`.
            </hard_safety>""";

    private static String planFirstSentence(@Nullable String mode) {
        return "plan".equalsIgnoreCase(mode)
                ? " PLAN FIRST: the human asked for a plan. Plan before your first edit, as above."
                : "";
    }

    private static String judgementSentence(List<String> on) {
        return on.isEmpty() ? ""
                : " Your first status report carries `switches`: for each of " + String.join(", ", on)
                + " `yes — <why>` or `no — <why>`, whether you judged it needed.";
    }

    private static String autoplanSentence(ConfigService.ConfigFile.AutoplanConfig autoplan) {
        return autoplan.enabledOrDefault()
                ? (" AUTOPLAN is on: before your first edit, plan first if the task changes more than %d"
                + " files, two of its requirements contradict, one contradicts what the code guarantees, a term it"
                + " rests on has no definition, or you would settle unasked what changes the code. `plan.md`'s first"
                + " line and your PLAN_PENDING message: `planned first: autoplan is on (agent.autoplan in jagt.yml),"
                + " <the criterion>`. If the task meets none of them, report IN_PROGRESS with outcome `no_plan` and"
                + " the reason before your first edit.").formatted(autoplan.filesOrDefault())
                : "";
    }

    private static String visualizeSentence(ConfigService.ConfigFile.VisualizeConfig visualize) {
        return visualize.enabledOrDefault()
                ? " VISUALIZE is on: while analysing the task, where you decide whether to plan first, judge whether"
                + " it is worth a picture. A two-line bug fix is not. If it is, write `" + Artifact.VISUAL.fileName()
                + "` in the worktree as part of the plan when you plan first, else before your first edit. A frontend"
                + " change gets an ASCII sketch of each screen, one fenced block per screen. A backend change gets Mermaid (class, sequence or ER diagram, in a"
                + " mermaid fence) and markdown tables; no `;` in a Mermaid label, where it ends the statement."
                + " Pictures over prose."
                : "";
    }

    private static String diagnoseSentence(ConfigService.ConfigFile.DiagnoseConfig diagnose) {
        return diagnose.enabledOrDefault()
                ? " DIAGNOSE: a bug is diagnosed from facts before code. Gather the report's evidence (error, logs,"
                + " profile, versions) and a control: the same case working, or failing louder for a known cause."
                + " A signal is what the broken case shows and the control does not. A cause stands only when it fits"
                + " every fact, each cited; when none fits, re-check a fact, then split, merge or combine causes, then"
                + " read one layer down (logs, network, process, kernel). A repro counts only when it shows the"
                + " report's signal."
                : "";
    }

    private static String baseBranches(List<NewRepo> repos) {
        String named = named(repos, repos.size(), NewRepo::baseBranch);
        return repos.size() == 1
                ? "The base branch " + named + " is read-only."
                : "The base branches " + named + " are read-only.";
    }

    private static String deployBranches(List<NewRepo> repos) {
        List<NewRepo> deployed = repos.stream()
                .filter(repo -> repo.config().deployBranch() != null && !repo.config().deployBranch().isBlank())
                .toList();
        String named = named(deployed, repos.size(), repo -> repo.config().deployBranch());
        return switch (deployed.size()) {
            case 0 -> "";
            case 1 -> " The deploy branch " + named + " is jagt's `deploy` alone.";
            default -> " The deploy branches " + named + " are jagt's `deploy` alone.";
        };
    }

    private static String named(List<NewRepo> repos, int ofTheTask, Function<NewRepo, String> branch) {
        return repos.stream()
                .map(repo -> "`" + branch.apply(repo) + "`" + (ofTheTask == 1 ? "" : " (" + repo.project() + ")"))
                .collect(Collectors.joining(", "));
    }

    private static String deployWorktrees(String taskId, List<NewRepo> repos) {
        String paths = GitDeploy.deployWorktreePaths(repos, taskId).stream().map(Path::toString)
                .collect(Collectors.joining(", "));
        return paths.isBlank() ? "" : "\n- Also yours while jagt hands you a deploy conflict there: " + paths
                + ". Finishing that merge (edit, `git add`) is not a `git merge` of yours.";
    }

    /** The task's OTHER worktrees, which this agent may edit as well, or a sentence saying there are none. */
    private static String alsoYours(NewRepo mine, List<NewRepo> repos) {
        String siblings = repos.stream()
                .filter(repo -> !repo.project().equals(mine.project()))
                .map(repo -> "  - " + repo.project() + ": " + repo.worktreePath()
                        + " (cut from " + repo.baseBranch() + ")")
                .collect(Collectors.joining("\n"));
        return siblings.isBlank()
                ? "- This task works in this repository only."
                : "- This task ALSO works in these worktrees, and you edit them yourself — same task, same"
                        + " branch name, one session:\n" + siblings
                        + "\n  Your instructions and your drafted review replies stay in THIS directory:"
                        + " task_context.md and review_replies.md are read from here, never from those.";
    }
}

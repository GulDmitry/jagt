package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.service.GitCommands;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.task.Remote;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What git says about one repository of a task that a session can act on: facts and a command. */
@Service
@RequiredArgsConstructor
public class GitCaseFacts {

  public static final int MAX_CANDIDATES = 10;

  private final GitCommands git;
  private final GitDeploy gitDeploy;

  /** A merge commit on a deploy branch naming the task. */
  public record Candidate(String sha, String date, String subject) {

    public String line() {
      return GitDeploy.shortSha(sha) + " " + date + " " + subject;
    }
  }

  /** The merges on the deploy branch naming the task, newest first, at most 10. */
  public List<Candidate> mergeCandidates(Path projectPath, String deployBranch, String taskId) {
    Remote remote = git.remote(projectPath);
    var log =
        git.run(
            projectPath,
            List.of(
                "git",
                "log",
                "--merges",
                "--fixed-strings",
                "--grep",
                taskId,
                "-n",
                String.valueOf(MAX_CANDIDATES),
                "--format=%H%x09%cs%x09%s",
                remote.track(deployBranch)));
    if (log.exitCode() != 0) {
      return List.of();
    }
    return log.stdout()
        .lines()
        .map(line -> line.split("\t", 3))
        .filter(parts -> parts.length == 3)
        .map(parts -> new Candidate(parts[0], parts[1], parts[2]))
        .toList();
  }

  /** The candidate a human's pick names: a full sha or a prefix of at least seven characters. */
  public Optional<Candidate> picked(List<Candidate> candidates, String pick) {
    String named = pick.strip().toLowerCase(java.util.Locale.ROOT);
    return named.length() < 7
        ? Optional.empty()
        : candidates.stream().filter(c -> c.sha().startsWith(named)).findFirst();
  }

  /** One line per git case the repository stands in; empty where it stands in none. */
  public List<String> cases(Path projectPath, String taskId, String project, String deployBranch) {
    Remote remote = git.remote(projectPath);
    List<String> cases = new ArrayList<>();
    if (gitDeploy.hasRevertWorktree(projectPath, taskId)) {
      cases.add(
          worktreeCase(
              project,
              "revert",
              GitDeploy.revertWorktreePath(projectPath, taskId),
              remote.track(deployBranch),
              "revert " + taskId));
    }
    if (gitDeploy.hasDeployWorktree(projectPath, taskId)) {
      cases.add(
          worktreeCase(
              project,
              "deploy",
              GitDeploy.deployWorktreePath(projectPath, taskId),
              remote.track(deployBranch),
              "deploy " + taskId));
    }
    divergence(projectPath, remote, taskId).ifPresent(found -> cases.add(project + ": " + found));
    return cases;
  }

  private String worktreeCase(
      String project, String kind, Path worktree, String track, String finish) {
    String unmerged = unmerged(worktree);
    String work =
        unmerged.isBlank()
            ? "run `git merge " + track + "` in it, resolve what conflicts, `git add` it"
            : "resolve " + unmerged + ", `git add` them";
    return project
        + ": the "
        + kind
        + " worktree "
        + worktree
        + " waits; "
        + work
        + ", then press `"
        + finish
        + "`.";
  }

  private String unmerged(Path worktree) {
    var result = git.run(worktree, List.of("git", "diff", "--name-only", "--diff-filter=U"));
    return result.exitCode() == 0 ? result.stdout().trim().replace('\n', ' ') : "";
  }

  private Optional<String> divergence(Path projectPath, Remote remote, String branch) {
    if (remote.local()
        || !exists(projectPath, remote.trackingRef(branch))
        || !exists(projectPath, "refs/heads/" + branch)) {
      return Optional.empty();
    }
    String[] counted =
        git.run(
                projectPath,
                List.of(
                    "git",
                    "rev-list",
                    "--left-right",
                    "--count",
                    remote.track(branch) + "..." + branch))
            .expectSuccess("git rev-list --count " + branch)
            .stdout()
            .strip()
            .split("\\s+", 0);
    if (Long.parseLong(counted[0]) == 0 || Long.parseLong(counted[1]) == 0) {
      return Optional.empty();
    }
    return Optional.of(
        "branch "
            + branch
            + " ("
            + GitDeploy.shortSha(head(projectPath, "refs/heads/" + branch))
            + ", "
            + counted[1]
            + " commit(s) only here) and "
            + remote.track(branch)
            + " ("
            + GitDeploy.shortSha(head(projectPath, remote.trackingRef(branch)))
            + ", "
            + counted[0]
            + " only there) diverged; run `git rebase "
            + remote.track(branch)
            + "` on it, then `git push --force-with-lease "
            + remote.pushTarget()
            + " "
            + branch
            + "`.");
  }

  private boolean exists(Path projectPath, String ref) {
    return git.run(projectPath, List.of("git", "rev-parse", "--verify", "--quiet", ref)).exitCode()
        == 0;
  }

  private String head(Path projectPath, String ref) {
    return git.run(projectPath, List.of("git", "rev-parse", ref))
        .expectSuccess("git rev-parse " + ref)
        .stdout()
        .trim();
  }
}

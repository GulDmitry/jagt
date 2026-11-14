package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.repo.Remote;

/** What a session resumed on an open review request is told to do, and not to do. */
public final class ResumeInstructions {

  private ResumeInstructions() {}

  public static String of(String taskId, String mrUrl, String targetBranch, Remote remote) {
    return "Reopened for review. Your branch is resumed with its existing commits and"
        + " review request "
        + mrUrl
        + " is open — there is NOTHING to build or commit right now."
        + " FIRST run `git status`: a rebase onto "
        + targetBranch
        + " in progress is jagt's, and it"
        + " conflicted — resolve the conflicts, `git rebase --continue`"
        + afterTheRebase(remote, taskId)
        + ", and report `question` where a resolution was not obvious. Otherwise do NOT"
        + " re-implement and do NOT call update_agent_status: the Master has already set your"
        + " status (CI_POLLING). Stay idle; only when the Master relays review comments via"
        + " task_context.md do you address them.";
  }

  private static String afterTheRebase(Remote remote, String taskId) {
    return remote.local()
        ? " (nothing is pushed: the repository stays on this machine)"
        : ", then `git push --force-with-lease "
            + remote.pushTarget()
            + " "
            + taskId
            + "` (this brief is the permission, for THIS branch only)";
  }
}

package dev.jagt.orchestrator.task.plan;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * A document one step of a task's life leaves behind for the next step to read, in the worktree
 * jagt cut. A capability declares which of them it touches rather than being handed the content:
 * the file on disk is what the agent is editing, so a copy passed through a signature would drift
 * from it.
 *
 * <p>Declared in the order a human reads them: the plan and the sketch first, the reviewer's own
 * notes last.
 *
 * <p>The briefing is not here — which file holds it is {@code AgentRuntime}'s answer, not the
 * task's.
 */
@RequiredArgsConstructor
public enum Artifact {

  /** What the agent means to do, before it writes anything. */
  PLAN("plan.md"),
  /** What the agent sketched of the change, before its first edit: screens, diagrams, tables. */
  VISUAL("task_visual.md"),
  /** The one instruction standing right now, and no older one. */
  CONTEXT("task_context.md"),
  /** What the task was opened with, which no relay overwrites. */
  REQUEST("task_request.md"),
  /**
   * What the agent learnt that the code and the history do not say, which a fresh session starts
   * from.
   */
  NOTES("task_notes.md"),
  /** What the agent means to answer, before anything is posted. */
  REPLIES("review_replies.md"),
  /**
   * What earlier rounds of this task decided, which a later reader does not reopen without a
   * blocking reason.
   */
  DECISIONS("master-decisions.md"),
  /** What the unattended reviewer concluded about the round, and the verdict jagt reads back. */
  REVIEW("master-review.md"),
  /** What a checking role concluded about the round, one file per role id. */
  ROLE_REPORT("*-report.md"),
  /** What the scripted flows recorded while they ran: their report, screenshots and traces. */
  EVIDENCE("acceptance/");

  private final String fileName;

  public String fileName() {
    return fileName;
  }

  /** A name with a wildcard or a folder stands for several paths, not one file. */
  public boolean isPlain() {
    return fileName.indexOf('*') < 0 && !fileName.endsWith("/");
  }

  /**
   * Every file name jagt writes into a worktree of its own accord, so no caller keeps a second
   * list.
   */
  public static List<String> fileNames() {
    return Arrays.stream(values()).filter(Artifact::isPlain).map(Artifact::fileName).toList();
  }

  /**
   * The same names as git's exclude spells them; a wildcard or folder is anchored to the root, so a
   * project's own nested file of that shape stays visible.
   */
  public static List<String> excludePatterns() {
    return Arrays.stream(values())
        .map(artifact -> artifact.isPlain() ? artifact.fileName : "/" + artifact.fileName)
        .toList();
  }

  /** The wildcard and folder names, which a reader expands against what the worktree holds. */
  public static List<String> expandable() {
    return Arrays.stream(values())
        .filter(artifact -> !artifact.isPlain())
        .map(Artifact::fileName)
        .toList();
  }
}

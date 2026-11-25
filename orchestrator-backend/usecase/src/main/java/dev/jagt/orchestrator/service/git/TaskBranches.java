package dev.jagt.orchestrator.service.git;

public final class TaskBranches {

  private TaskBranches() {}

  public static String deploy(String sourceBranch) {
    return "jagt-deploy-" + sourceBranch;
  }

  static String revert(String sourceBranch) {
    return "jagt-revert-" + sourceBranch;
  }
}

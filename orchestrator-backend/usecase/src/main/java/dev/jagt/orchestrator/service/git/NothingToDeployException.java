package dev.jagt.orchestrator.service.git;

/**
 * The branch holds nothing the target does not already have; typed so a caller can tell it from a
 * failure.
 */
public class NothingToDeployException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public NothingToDeployException(String sourceBranch, String targetBranch) {
    super(
        "Nothing to deploy: branch '"
            + sourceBranch
            + "' has no commits beyond "
            + targetBranch
            + " (commit work first, or it is already deployed).");
  }

  NothingToDeployException(String message) {
    super(message);
  }
}

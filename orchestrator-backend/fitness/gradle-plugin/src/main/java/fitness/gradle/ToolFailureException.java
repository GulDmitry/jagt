package fitness.gradle;

/** A tool the build runs exited with a failure code or wrote to its error stream. */
public final class ToolFailureException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int exitCode;

  public ToolFailureException(String tool, int exitCode, String stderr) {
    super(tool + " exited " + exitCode + (stderr.isEmpty() ? "" : ": " + stderr));
    this.exitCode = exitCode;
  }

  public int exitCode() {
    return exitCode;
  }
}

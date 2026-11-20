package fitness.gradle;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.flow.BuildWorkResult;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/** Names the function, the run and its limit when the build ends because a task the time rows limit ran past it. */
public abstract class Overruns implements FlowAction<Overruns.Parameters> {

  private static final Logger LOGGER = Logging.getLogger(Overruns.class);
  private static final String TIMEOUT = "Timeout has been exceeded";
  private static final Pattern STOPPED = Pattern.compile("Execution failed for task '([^']+)'");

  /** The build's result, and the task path to "function id|limit in minutes". */
  public interface Parameters extends FlowParameters {
    @Input
    Property<BuildWorkResult> getResult();

    @Input
    MapProperty<String, String> getLimits();
  }

  static String message(String function, String path, String minutes) {
    return "fitness: %s: %s ran past its limit of %s minutes".formatted(function, path, minutes);
  }

  @Override
  public void execute(Parameters parameters) {
    Optional<Throwable> failure = parameters.getResult().get().getFailure();
    for (Throwable cause = failure.orElse(null); cause != null; cause = cause.getCause()) {
      Matcher stopped = STOPPED.matcher(String.valueOf(cause.getMessage()));
      if (stopped.find() && timedOut(cause)) {
        String limit = parameters.getLimits().getOrElse(Map.of()).get(stopped.group(1));
        if (limit != null) {
          String[] parts = limit.split("\\|");
          LOGGER.error(message(parts[0], stopped.group(1), parts[1]));
        }
      }
    }
  }

  private static boolean timedOut(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (String.valueOf(cause.getMessage()).contains(TIMEOUT)) {
        return true;
      }
    }
    return false;
  }
}

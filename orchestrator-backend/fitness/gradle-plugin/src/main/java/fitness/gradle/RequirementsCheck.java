package fitness.gradle;

import fitness.core.FitnessConfigException;
import fitness.core.Reason;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Fails with the typed load error when a tool a required row needs is not there. */
@DisableCachingByDefault(because = "it looks at the machine as it is now")
public abstract class RequirementsCheck extends DefaultTask {

  @Input
  public abstract MapProperty<String, String> getMissing();

  @TaskAction
  public void check() {
    for (Map.Entry<String, String> gap : getMissing().get().entrySet()) {
      throw new FitnessConfigException(Reason.MISSING_TOOL, gap.getKey());
    }
  }
}

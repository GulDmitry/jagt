package fitness.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public abstract class FitnessExtension {

  /** The `fitness.yml` whose functions the build measures. */
  public abstract RegularFileProperty getFile();

  /** The Gradle property whose number replaces every minutes ceiling for one run. */
  public abstract Property<String> getMinutesProperty();

  /** False drops every time row: no task gets a time limit and no run is timed. */
  public abstract Property<Boolean> getTimed();

  /** System properties set on each suite a contexts row measures, so that the suite logs its context cache. */
  public abstract MapProperty<String, String> getContextsSystemProperties();

  /** What a suite's report line looks like when it counts contexts: group 1 names the cache, group 2 its miss count. */
  public abstract Property<String> getContextsPattern();

  /** What the `fitness` task runs the functions with: the engines, the project's function classes and its compiled code. */
  public abstract ConfigurableFileCollection getClasspath();
}

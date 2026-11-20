package fitness.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;

public abstract class FitnessExtension {

  /** The `fitness.yml` whose functions the build measures. */
  public abstract RegularFileProperty getFile();

  /** What the `fitness` task runs the functions with: the engines, the project's function classes and its compiled code. */
  public abstract ConfigurableFileCollection getClasspath();
}

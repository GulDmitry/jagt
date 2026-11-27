package technical.maintainability.analysability;

import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class JobCatchFitness implements Fitness {

  private static final Pattern BROAD_CATCH =
      Pattern.compile("\\bcatch \\([^)]*\\b(?:RuntimeException|Exception|Throwable)\\b");
  private static final Pattern REPORTS = Pattern.compile("\\bfaulted\\(|\\bthrow\\b");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    List<String> unreported = new ArrayList<>();
    SourceRoots.mainJavaFiles()
        .filter(file -> file.toString().contains("/job/"))
        .filter(file -> !file.endsWith("Jobs.java"))
        .forEach(file -> unreported.addAll(CatchBodies.without(file, BROAD_CATCH, REPORTS)));
    return Measurement.of(unreported);
  }
}

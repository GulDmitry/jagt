package technical.maintainability.analysability;

import dev.jagt.orchestrator.SourceRoots;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class SilentCatchFitness implements Fitness {

  private static final Pattern CATCH = Pattern.compile("\\bcatch \\(");
  private static final Pattern REPORTS = Pattern.compile("\\blog\\.|\\bthrow\\b");

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    List<String> silent = new ArrayList<>();
    SourceRoots.mainJavaFiles()
        .forEach(file -> silent.addAll(CatchBodies.without(file, CATCH, REPORTS)));
    return Measurement.of(silent);
  }
}

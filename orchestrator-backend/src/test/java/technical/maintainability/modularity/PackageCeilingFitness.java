package technical.maintainability.modularity;

import dev.jagt.orchestrator.PackageMap;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.Map;
import java.util.Set;

public final class PackageCeilingFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    Set<String> exceptions = PackageMap.overTen();
    Map<String, Long> counted = PackageMap.onDisk();
    long limit = function.threshold().limit();
    long largest =
        counted.entrySet().stream()
            .filter(entry -> !exceptions.contains(entry.getKey()))
            .mapToLong(Map.Entry::getValue)
            .max()
            .orElse(0);
    return new Measurement(
        largest,
        counted.entrySet().stream()
            .filter(entry -> !exceptions.contains(entry.getKey()))
            .filter(entry -> entry.getValue() > limit)
            .map(entry -> entry.getValue() + " " + entry.getKey())
            .sorted()
            .toList());
  }
}

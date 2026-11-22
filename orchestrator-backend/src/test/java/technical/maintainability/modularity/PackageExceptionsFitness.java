package technical.maintainability.modularity;

import dev.jagt.orchestrator.PackageMap;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.List;
import java.util.Map;

public final class PackageExceptionsFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    Map<String, Long> counted = PackageMap.onDisk();
    long smallest =
        PackageMap.overTen().stream()
            .mapToLong(name -> counted.getOrDefault(name, 0L))
            .min()
            .orElse(Long.MAX_VALUE);
    List<String> items =
        PackageMap.overTen().stream()
            .filter(name -> counted.getOrDefault(name, 0L) < function.threshold().limit())
            .map(name -> counted.getOrDefault(name, 0L) + " " + name)
            .sorted()
            .toList();
    return new Measurement(smallest, items);
  }
}

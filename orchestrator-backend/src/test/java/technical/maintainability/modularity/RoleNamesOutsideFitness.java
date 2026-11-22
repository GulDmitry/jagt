package technical.maintainability.modularity;

import dev.jagt.orchestrator.RoleFolders;
import dev.jagt.orchestrator.RoleWords;
import fitness.core.Fitness;
import fitness.core.FitnessFunction;
import fitness.core.Measurement;
import fitness.core.Project;
import java.util.List;
import java.util.stream.Stream;

public final class RoleNamesOutsideFitness implements Fitness {

  @Override
  public Measurement measure(FitnessFunction function, Project project) {
    List<String> ids = RoleFolders.ids();
    return Measurement.of(
        Stream.of(
                RoleWords.inSurface(ids).stream(),
                RoleWords.inBoard(ids).stream(),
                RoleWords.inCoreAndUsecase(ids).stream())
            .flatMap(items -> items)
            .toList());
  }
}

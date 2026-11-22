package fitness.core.load;

import fitness.core.FitnessFunction;
import fitness.core.Project;
import java.util.List;

public record FitnessConfig(Project project, List<FitnessFunction> functions) {}

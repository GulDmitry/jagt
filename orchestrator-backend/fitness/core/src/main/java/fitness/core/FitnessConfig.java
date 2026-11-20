package fitness.core;

import java.util.List;

public record FitnessConfig(Project project, List<FitnessFunction> functions) {}

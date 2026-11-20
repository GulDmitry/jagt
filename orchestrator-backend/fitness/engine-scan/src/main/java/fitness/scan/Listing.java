package fitness.scan;

import fitness.core.Project;
import fitness.core.FitnessFunction;
import java.nio.file.Path;
import java.util.List;

/** Names every unit found in the selected files that breaks what the measure asks for. */
interface Listing {

  List<String> items(FitnessFunction function, Project project, List<Path> files);
}

package fitness.scan;

import fitness.core.FitnessFunction;
import fitness.core.Project;
import java.nio.file.Path;
import java.util.List;

/** Measures every unit found in the selected files; the function's threshold decides which are over. */
interface Sizing {

  List<Sized> sizes(FitnessFunction function, Project project, List<Path> files);
}

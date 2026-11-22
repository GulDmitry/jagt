package fitness.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** {@code modules} maps each Gradle module's directory name to the full package patterns other modules may reference, {@code ..} for every package. */
public record Project(
    Path root, String basePackage, List<String> sourceSets, Map<String, List<String>> modules) {}

package fitness.core;

import java.nio.file.Path;
import java.util.List;

public record Project(Path root, String basePackage, List<String> sourceSets) {}

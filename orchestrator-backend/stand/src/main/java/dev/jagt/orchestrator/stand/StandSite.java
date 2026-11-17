package dev.jagt.orchestrator.stand;

import java.nio.file.Path;
import java.util.Optional;

record StandSite(Path root, StandProject project, StandHost host, Optional<Process> awake) {}

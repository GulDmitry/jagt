package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class StandEvidence {

    private static final List<String> FILES = List.of("timeline.log", "jagt-backend.log", "jagt.out", "screens.log",
            "jagt.out" + JagtProcess.BEFORE_RESTART, "jagt-backend.log" + JagtProcess.BEFORE_RESTART,
            "state.json", "misses.jsonl", "recording.jsonl", StandMarker.FILE);

    public static final Path DIR = Path.of("build", "stand", "failed");

    private StandEvidence() {
    }

    public static Path keep(Path root, String scenario, Path failedDir) {
        Path target = failedDir.resolve(scenario.replace('/', '-') + "-" + root.getFileName());
        try {
            Files.createDirectories(target);
            for (String name : FILES) {
                if (Files.isRegularFile(root.resolve(name))) {
                    Files.copy(root.resolve(name), target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new StandFailure("cannot keep the evidence of " + root, e);
        }
        return target;
    }
}

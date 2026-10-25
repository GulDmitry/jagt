package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

record BackendSignals(int masterReads) {

    private static final String MASTER = "\"message\":\"master ";

    static BackendSignals read(Path log) {
        return new BackendSignals((int) lines(log).stream().filter(line -> line.contains(MASTER)).count());
    }

    private static List<String> lines(Path log) {
        if (!Files.isRegularFile(log)) {
            return List.of();
        }
        try {
            return Files.readAllLines(log);
        } catch (IOException e) {
            throw new StandFailure("cannot read " + log, e);
        }
    }
}

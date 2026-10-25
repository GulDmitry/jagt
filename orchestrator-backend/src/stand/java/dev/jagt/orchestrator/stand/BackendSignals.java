package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

record BackendSignals(int masterReads, boolean waitingOnTheReviewer) {

    private static final String MASTER = "\"message\":\"master ";
    private static final String SWEEP = "\"message\":\"sweep done\"";
    private static final String WAITING = "waiting on the reviewer";
    private static final int SWEEPS = 3;

    static BackendSignals read(Path log) {
        List<String> lines = lines(log);
        List<String> sweeps = lines.stream().filter(line -> line.contains(SWEEP)).toList();
        boolean waiting = sweeps.size() >= SWEEPS && sweeps.subList(sweeps.size() - SWEEPS, sweeps.size()).stream()
                .allMatch(line -> line.contains(WAITING));
        return new BackendSignals((int) lines.stream().filter(line -> line.contains(MASTER)).count(), waiting);
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

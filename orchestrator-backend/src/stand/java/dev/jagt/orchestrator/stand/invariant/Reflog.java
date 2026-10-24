package dev.jagt.orchestrator.stand.invariant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Reflog {

    private static final Pattern ENTRY = Pattern.compile("([0-9a-f]+) ([0-9a-f]+) .*> (\\d+) [+-]\\d{4}\t.*");

    private Reflog() {
    }

    static List<RunRecord.RefUpdate> read(Path origin, String branch) {
        Path log = origin.resolve("logs").resolve("refs").resolve("heads").resolve(branch);
        if (!Files.isRegularFile(log)) {
            return List.of();
        }
        return StateFiles.lines(log).map(ENTRY::matcher).filter(Matcher::matches)
                .map(entry -> new RunRecord.RefUpdate(entry.group(1), entry.group(2),
                        Instant.ofEpochSecond(Long.parseLong(entry.group(3))), entry.group()))
                .toList();
    }
}

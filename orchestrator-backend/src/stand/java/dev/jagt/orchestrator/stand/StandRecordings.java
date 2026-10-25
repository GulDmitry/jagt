package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StandRecordings {

    public static final Path DIR = Path.of("build", "stand", "recordings");
    static final int KEPT = 20;

    private static final Pattern NAME = Pattern.compile(".*" + Pattern.quote(StandRoots.PREFIX) + "(\\d+)-\\d+\\.jsonl");

    private StandRecordings() {
    }

    static synchronized void fold(Path dir) throws IOException {
        Map<String, List<Path>> byRun;
        try (Stream<Path> files = Files.list(dir)) {
            byRun = files.filter(file -> NAME.matcher(file.getFileName().toString()).matches())
                    .collect(Collectors.groupingBy(StandRecordings::pid));
        }
        List<String> oldestFirst = byRun.entrySet().stream()
                .sorted(Comparator.comparing(run -> newest(run.getValue()))).map(Map.Entry::getKey).toList();
        for (String run : oldestFirst.subList(0, Math.max(0, oldestFirst.size() - KEPT))) {
            for (Path recording : byRun.get(run)) {
                Files.delete(recording);
            }
        }
    }

    private static String pid(Path file) {
        Matcher name = NAME.matcher(file.getFileName().toString());
        name.matches();
        return name.group(1);
    }

    private static FileTime newest(List<Path> files) {
        return files.stream().map(StandRecordings::modified).max(Comparator.naturalOrder()).orElseThrow();
    }

    private static FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            throw new StandFailure("cannot read the age of " + file, e);
        }
    }
}

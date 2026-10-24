package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class StandRoots {

    public static final String PREFIX = "jagt-stand-";
    private static final Pattern NAME = Pattern.compile(Pattern.quote(PREFIX) + "(\\d+)-(\\d+)");

    public record Found(Path root, StandMarker marker) {
    }

    public record Plan(List<Found> stop, List<Found> remove, List<Found> keep) {
    }

    private StandRoots() {
    }

    public static Plan plan(Path base, boolean failedToo, LongPredicate alive) {
        List<Found> stop = new ArrayList<>();
        List<Found> remove = new ArrayList<>();
        List<Found> keep = new ArrayList<>();
        for (Found found : under(base)) {
            if (alive.test(found.marker().pid())) {
                continue;
            }
            stop.add(found);
            (found.marker().failed() && !failedToo ? keep : remove).add(found);
        }
        return new Plan(stop, remove, keep);
    }

    public static boolean named(Path directory) {
        return NAME.matcher(directory.getFileName().toString()).matches();
    }

    private static List<Found> under(Path base) {
        try (Stream<Path> entries = Files.list(base)) {
            return entries.filter(Files::isDirectory).filter(dir -> !Files.isSymbolicLink(dir))
                    .map(StandRoots::found).flatMap(Stream::ofNullable).sorted((a, b) -> a.root().compareTo(b.root()))
                    .toList();
        } catch (IOException e) {
            throw new StandFailure("cannot list " + base, e);
        }
    }

    private static Found found(Path dir) {
        Matcher name = NAME.matcher(dir.getFileName().toString());
        if (!name.matches()) {
            return null;
        }
        return new Found(dir, StandMarker.read(dir, Long.parseLong(name.group(1))));
    }
}

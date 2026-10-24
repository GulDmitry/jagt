package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TimelineFile {

    public static final String RELAY = "relay";
    public static final String WROTE_DEPLOY_BRANCH = "wrote-deploy-branch";
    private static final String STAND = "stand";

    private TimelineFile() {
    }

    public static String relayLine(String task, List<String> steps) {
        return RELAY + " " + task + " " + String.join(",", steps);
    }

    public static String wroteLine(String sha) {
        return WROTE_DEPLOY_BRANCH + " " + sha;
    }

    static RunRecord.Timeline read(Path file) {
        List<RunRecord.Sample> samples = new ArrayList<>();
        List<RunRecord.Relay> relays = new ArrayList<>();
        Set<String> wrote = new HashSet<>();
        if (Files.isRegularFile(file)) {
            StateFiles.lines(file).map(line -> line.split(" ")).filter(words -> words.length >= 3).forEach(words -> {
                Instant at = Instant.parse(words[0]);
                String line = String.join(" ", words);
                if (!words[1].equals(STAND)) {
                    samples.add(new RunRecord.Sample(at, words[1], TaskStatus.valueOf(words[2]), line));
                } else if (words[2].equals(RELAY) && words.length >= 5) {
                    relays.add(new RunRecord.Relay(at, words[3], Arrays.asList(words[4].split(",")), line));
                } else if (words[2].equals(WROTE_DEPLOY_BRANCH) && words.length >= 4) {
                    wrote.add(words[3]);
                }
            });
        }
        return new RunRecord.Timeline(List.copyOf(samples), List.copyOf(relays), Set.copyOf(wrote));
    }
}

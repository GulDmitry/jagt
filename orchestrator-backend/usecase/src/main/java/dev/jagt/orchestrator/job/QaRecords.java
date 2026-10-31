package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.QaEnding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What each task's last QA check ended in, kept beside {@code state.json}: one check per deploy, across a restart. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QaRecords {

    private static final String TAB = "\t";

    /** {@code digest} is the deploy's, {@code sha} what the check read, {@code at} when it ended. */
    public record Record(String digest, String sha, QaEnding ending, long at) {
    }

    private final OrchestratorPaths paths;
    private final Clock clock;

    public synchronized Optional<Record> latest(String taskId) {
        return Optional.ofNullable(all().get(taskId));
    }

    /** True where this deploy's check ended or waits on its environment, and is not one to make again yet. */
    public boolean settled(String taskId, String digest) {
        return latest(taskId).filter(record -> record.digest().equals(digest)).isPresent();
    }

    /** The environment answered: the waiting check is no longer held and runs again. */
    public synchronized void released(String taskId, String digest) {
        Map<String, Record> kept = all();
        Record record = kept.get(taskId);
        if (record != null && record.digest().equals(digest) && record.ending() == QaEnding.WAITING) {
            kept.remove(taskId);
            write(kept);
        }
    }

    public synchronized void put(String taskId, String digest, String sha, QaEnding ending) {
        Map<String, Record> kept = all();
        kept.put(taskId, new Record(digest, sha, ending, clock.millis()));
        write(kept);
    }

    private void write(Map<String, Record> kept) {
        try {
            Files.write(paths.qaVerdicts(), kept.entrySet().stream().map(entry -> entry.getKey() + TAB
                    + entry.getValue().digest() + TAB + entry.getValue().sha() + TAB + entry.getValue().ending()
                    + TAB + entry.getValue().at()).toList());
        } catch (IOException unwritten) {
            log.atWarn().setMessage("qa verdicts not kept").addKeyValue("file", paths.qaVerdicts())
                    .addKeyValue("cause", unwritten.toString()).log();
        }
    }

    private Map<String, Record> all() {
        Map<String, Record> read = new LinkedHashMap<>();
        for (String line : lines()) {
            String[] part = line.split(TAB);
            if (part.length == 5) {
                read.put(part[0], new Record(part[1], part[2], QaEnding.valueOf(part[3]), Long.parseLong(part[4])));
            }
        }
        return read;
    }

    private List<String> lines() {
        try {
            return Files.isRegularFile(paths.qaVerdicts()) ? Files.readAllLines(paths.qaVerdicts()) : List.of();
        } catch (IOException unread) {
            log.atWarn().setMessage("qa verdicts unread").addKeyValue("file", paths.qaVerdicts())
                    .addKeyValue("cause", unread.toString()).log();
            return List.of();
        }
    }
}

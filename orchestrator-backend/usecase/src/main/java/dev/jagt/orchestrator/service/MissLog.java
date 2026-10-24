package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;

/** A step no case sends, one line each beside `state.json`. */
@Service
@Slf4j
public class MissLog {

    public record Miss(long at, String task, String step, TaskStatus status, String by) {
    }

    private final ObjectMapper mapper;
    private final RollingFile file;
    private final Clock clock;

    public MissLog(ObjectMapper mapper, OrchestratorPaths paths, Clock clock) {
        this.mapper = mapper;
        this.file = new RollingFile(paths.stateFile().resolveSibling("misses.jsonl"));
        this.clock = clock;
    }

    public void record(String task, String step, TaskStatus status, CaseBook.By by) {
        try {
            file.append(mapper.writeValueAsString(new Miss(clock.millis(), task, step, status, by.id())) + "\n");
        } catch (IOException e) {
            log.atWarn().setMessage("miss not recorded").addKeyValue("file", file.path())
                    .addKeyValue("task", task).addKeyValue("cause", e.toString()).log();
        }
    }
}

package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** Every stop jagt put on a task, one line each beside `state.json`; a lift leaves them. */
@Service
@Slf4j
public class StopLog {

    public record Stop(long at, String task, String alias, String trigger, TaskStatus status, String evidence) {
    }

    private final ObjectMapper mapper;
    private final RollingFile file;

    public StopLog(ObjectMapper mapper, OrchestratorPaths paths) {
        this.mapper = mapper;
        this.file = new RollingFile(paths.stateFile().resolveSibling("stops.jsonl"));
    }

    public void record(Stop stop) {
        try {
            file.append(mapper.writeValueAsString(stop) + "\n");
        } catch (IOException e) {
            log.atWarn().setMessage("stop not recorded").addKeyValue("file", file.path())
                    .addKeyValue("task", stop.task()).addKeyValue("cause", e.toString()).log();
        }
    }
}

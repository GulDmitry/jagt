package dev.jagt.orchestrator.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;

/** A step no case sends, or a reply no case takes, one line each beside `state.json`. */
@Service
@Slf4j
public class MissLog {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Miss(long at, String task, String step, TaskStatus status, String by, @Nullable String got,
                       @Nullable String expected) {
    }

    private static final String SESSION = "session";

    private final ObjectMapper mapper;
    private final RollingFile file;
    private final Clock clock;

    public MissLog(ObjectMapper mapper, OrchestratorPaths paths, Clock clock) {
        this.mapper = mapper;
        this.file = new RollingFile(paths.stateFile().resolveSibling("misses.jsonl"));
        this.clock = clock;
    }

    public void record(String task, String step, TaskStatus status, CaseBook.By by) {
        write(new Miss(clock.millis(), task, step, status, by.id(), null, null));
    }

    /** A session's reply to {@code step} that no case of it takes. */
    public void refused(String task, String step, TaskStatus status, String got, String expected) {
        write(new Miss(clock.millis(), task, step, status, SESSION, got, expected));
    }

    private void write(Miss miss) {
        try {
            file.append(mapper.writeValueAsString(miss) + "\n");
        } catch (IOException e) {
            log.atWarn().setMessage("miss not recorded").addKeyValue("file", file.path())
                    .addKeyValue("task", miss.task()).addKeyValue("cause", e.toString()).log();
        }
    }
}

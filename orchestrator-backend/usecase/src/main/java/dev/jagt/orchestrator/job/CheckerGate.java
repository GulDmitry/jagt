package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.ProjectConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Whether a checker stands in for the session's deploy check, and the projects a deploy check names. */
@Service
@RequiredArgsConstructor
public class CheckerGate {

    private final ConfigService config;

    public boolean checking() {
        return config.load().qa().enabledOrDefault();
    }

    public Map<String, ProjectConfig> projects() {
        return config.load().projects();
    }
}

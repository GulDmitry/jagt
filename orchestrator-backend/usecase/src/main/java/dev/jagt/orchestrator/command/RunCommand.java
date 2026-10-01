package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.job.Jobs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RunCommand implements GlobalCommand {

    private final Jobs jobs;

    @Override
    public String id() {
        return "run";
    }

    @Override
    public String hint() {
        return "run an unattended job now instead of at its next tick";
    }

    @Override
    public List<String> usage() {
        return List.of("run <job>");
    }

    @Override
    public String run(String tail) {
        String id = tail.strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("usage: run <job> — `jobs` lists them");
        }
        jobs.runNow(id);
        return id + " runs within a second; `jobs` shows when it ran";
    }
}

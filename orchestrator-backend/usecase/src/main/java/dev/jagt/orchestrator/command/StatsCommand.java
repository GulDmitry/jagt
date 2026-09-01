package dev.jagt.orchestrator.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatsCommand implements GlobalCommand {

    private final StatsReport stats;

    @Override
    public String id() {
        return "stats";
    }

    @Override
    public String hint() {
        return "what jagt's own model calls cost, and where each task's time went";
    }

    @Override
    public boolean report() {
        return true;
    }

    @Override
    public String run(String tail) {
        return stats.render();
    }
}

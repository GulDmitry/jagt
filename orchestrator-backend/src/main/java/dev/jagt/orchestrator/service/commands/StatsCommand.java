package dev.jagt.orchestrator.service.commands;

import dev.jagt.orchestrator.service.GlobalCommand;
import dev.jagt.orchestrator.service.StateViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatsCommand implements GlobalCommand {

    private final StateViews views;

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
        return views.stats();
    }
}

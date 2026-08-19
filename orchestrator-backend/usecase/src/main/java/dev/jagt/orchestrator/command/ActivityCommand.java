package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.command.StateViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActivityCommand implements GlobalCommand {

    private final StateViews views;

    @Override
    public String id() {
        return "activity";
    }

    @Override
    public String hint() {
        return "what jagt did on its own, newest first";
    }

    @Override
    public boolean report() {
        return true;
    }

    @Override
    public String run(String tail) {
        return views.activity();
    }
}

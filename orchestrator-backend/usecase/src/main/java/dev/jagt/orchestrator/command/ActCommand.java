package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ActStops;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActCommand implements GlobalCommand {

    private final ActStops actStops;

    @Override
    public String id() {
        return "act";
    }

    @Override
    public int rank() {
        return 185;
    }

    @Override
    public String hint() {
        return "give the Master back the buttons it stopped pressing, and lift the day\u2019s ceiling on unattended starts";
    }

    @Override
    public String run(String tail) {
        return actStops.lift() ? "act restored: the Master presses again where `master.mode` is act"
                : "act was not stopped";
    }
}

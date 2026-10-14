package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.master.MasterBrake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActCommand implements GlobalCommand {

    private final MasterBrake brake;

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
        return "give the Master back the buttons it stopped pressing after three bad endings in a row";
    }

    @Override
    public String run(String tail) {
        return brake.restore() ? "act restored: the Master presses again where `master.mode` is act"
                : "act was not stopped";
    }
}

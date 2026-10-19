package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.DailyStarts;
import dev.jagt.orchestrator.service.master.MasterBrake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActCommand implements GlobalCommand {

    private final MasterBrake brake;
    private final DailyStarts dailyStarts;

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
        boolean stopped = brake.restore();
        stopped |= dailyStarts.lift();
        return stopped ? "act restored: the Master presses again where `master.mode` is act"
                : "act was not stopped";
    }
}

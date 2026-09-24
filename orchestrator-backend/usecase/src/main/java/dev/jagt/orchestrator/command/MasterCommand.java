package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.MasterMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MasterCommand implements GlobalCommand {

    private final ConfigService configService;

    @Override
    public String id() {
        return "master";
    }

    @Override
    public String hint() {
        return "the unattended reviewer: what it may do, and what it judges by";
    }

    @Override
    public boolean report() {
        return true;
    }

    @Override
    public String run(String tail) {
        ConfigService.ConfigFile.MasterConfig master = configService.load().master();
        MasterMode mode = master.modeOrOff();
        if (mode == MasterMode.OFF) {
            return "master: off. It is experimental: set `orchestrator.master.mode` to "
                    + MasterMode.JUDGE.id() + " and point `brief` at the file saying what it judges.";
        }
        return "master: " + mode.id() + (mode == MasterMode.JUDGE
                ? " — it reads and writes what it found; it presses nothing."
                : " — it also presses the button you would have.")
                + "\n  judges by: " + master.brief()
                + "\n  running:   nothing yet; the session itself is not built.";
    }
}

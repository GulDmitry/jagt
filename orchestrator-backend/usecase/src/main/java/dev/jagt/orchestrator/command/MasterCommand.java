package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.MasterSession;
import dev.jagt.orchestrator.service.MasterSpend;
import dev.jagt.orchestrator.service.TokenFormat;
import dev.jagt.orchestrator.task.MasterMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MasterCommand implements GlobalCommand {

    private final ConfigService configService;
    private final MasterSession master;
    private final MasterSpend spend;

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
        ConfigService.ConfigFile.MasterConfig config = configService.load().master();
        MasterMode mode = config.modeOrOff();
        if (mode == MasterMode.OFF) {
            return "master: off, and experimental. To try it: copy master-brief.md.dist to "
                    + config.briefOrDefault() + ", edit it until it reads like you, and set"
                    + " `orchestrator.master.mode` to " + MasterMode.JUDGE.id() + ".";
        }
        return "master: " + mode.id() + (mode == MasterMode.JUDGE
                ? " — it reads and writes what it found; it presses nothing."
                : " — it also presses the button you would have.")
                + "\n  judges by: " + config.briefOrDefault()
                + "\n  model:     " + (config.modelOrInherited().isEmpty()
                        ? "inherited from the agent CLI" : config.modelOrInherited())
                + "\n  running:   " + (master.live() ? "yes" : "no — the next tick starts it")
                + "\n  withheld:  " + (config.withholdOrNone().isEmpty()
                        ? "nothing — it holds every right a human has but `done`"
                        : String.join(", ", config.withholdOrNone()))
                + "\n  spent:     " + TokenFormat.compact(spend.total().total())
                + " tokens, $" + String.format(java.util.Locale.ROOT, "%.2f", spend.total().costUsd())
                + " — its own line, no task's";
    }
}

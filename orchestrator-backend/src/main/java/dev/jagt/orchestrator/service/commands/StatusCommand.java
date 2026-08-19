package dev.jagt.orchestrator.service.commands;

import dev.jagt.orchestrator.service.GlobalCommand;
import org.springframework.stereotype.Component;

/** Console-only because the board never hides the dashboard: there, this verb would open a copy of the page. */
@Component
public class StatusCommand implements GlobalCommand {

    @Override
    public String id() {
        return "status";
    }

    @Override
    public String hint() {
        return "show the dashboard";
    }

    @Override
    public boolean consoleOnly() {
        return true;
    }

    /** Blank: the shell redraws the dashboard after every line anyway, and printing it twice reads as a bug. */
    @Override
    public String run(String tail) {
        return "";
    }
}

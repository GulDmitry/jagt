package dev.jagt.orchestrator.command;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class HelpCommand implements GlobalCommand {

    /** Deferred: the reference lists every command, and this is one of them — injected directly it would be a cycle. */
    private final ObjectProvider<GlobalCommands> commands;

    public HelpCommand(ObjectProvider<GlobalCommands> commands) {
        this.commands = commands;
    }

    @Override
    public String id() {
        return "help";
    }

    @Override
    public String hint() {
        return "how jagt works";
    }

    @Override
    public boolean report() {
        return true;
    }

    @Override
    public String run(String tail) {
        return CommandReference.text(commands.getObject().all());
    }
}

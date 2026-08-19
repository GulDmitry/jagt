package dev.jagt.orchestrator.surface.ui;

import dev.jagt.orchestrator.surface.console.MasterShell;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * It owns the terminal until the human exits, and stopping the backend afterwards is part of
 * {@link MasterShell#run()} — agents live in tmux, so that never touches them.
 */
@Component
@ConditionalOnExpression("'${orchestrator.ui:web}'.matches('tui|both')")
@RequiredArgsConstructor
public class TuiOperatorUi implements OperatorUi {

    private final MasterShell masterShell;

    @Override
    public void start() {
        masterShell.run();
    }

    @Override
    public String name() {
        return "console TUI";
    }

    @Override
    public boolean blocking() {
        return true;
    }
}

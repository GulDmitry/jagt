package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.SessionLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** An agent CLI that keeps no log jagt reads spends nothing it can see. */
@Component
@ConditionalOnExpression("!'${orchestrator.agent.cli:claude}'.matches('claude|codex')")
public class NoSessionLog implements SessionLog {

    @Override
    public Spent spent(Path log, long from, long limit) {
        return Spent.nothing(from);
    }
}

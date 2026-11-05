package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** The round reads run on OpenCode where {@code master} is named under {@code orchestrator.opencode.reads}, else on {@code claude -p}. */
@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
public class ChosenRoundReviewer implements RoundReviewer {

    private final HeadlessClaudeRoundReviewer claude;
    private final OpenCodeRoundReviewer opencode;
    private final OpenCodeProperties properties;

    @Override
    public Answer<Judgement> review(Round round) {
        return onOpenCode() ? opencode.review(round) : claude.review(round);
    }

    @Override
    public boolean loadsMcpServer() {
        return onOpenCode() ? opencode.loadsMcpServer() : claude.loadsMcpServer();
    }

    private boolean onOpenCode() {
        return properties.readModel(OpenCodeRoundReviewer.ROLE).isPresent();
    }
}

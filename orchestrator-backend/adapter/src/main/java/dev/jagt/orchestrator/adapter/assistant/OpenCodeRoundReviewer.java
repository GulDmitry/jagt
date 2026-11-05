package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.protocol.RoundRead;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
class OpenCodeRoundReviewer {

    static final String ROLE = "master";
    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private final HeadlessOpenCode headless;
    private final OpenCodeProperties opencode;

    Answer<RoundReviewer.Judgement> review(RoundReviewer.Round round) {
        String model = opencode.readModel(ROLE).orElseThrow(() -> new IllegalStateException("no OpenCode model for " + ROLE));
        String prompt = round.shared().isBlank() ? round.prompt() : round.shared() + "\n\n" + round.prompt();
        OpenCodeScope scope = OpenCodeScope.of(RoundReadTools.READS,
                Stream.concat(RoundReadTools.REFUSED.stream(), RoundReadTools.BUILDS.stream()).toList(), false,
                round.worktrees(), List.of());
        return RoundAnswers.judged(headless.run(new OpenCodeRead(round.worktrees().getFirst(), prompt,
                RoundRead.SCHEMA.json(), model, scope, AssistantCallKind.MASTER_REVIEW, TIMEOUT)));
    }

    boolean loadsMcpServer() {
        return false;
    }
}

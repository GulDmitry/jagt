package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProperties;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.QaDecline;
import dev.jagt.orchestrator.task.QaVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnExpression(HeadlessClaude.NOT_REPLAYED)
@RequiredArgsConstructor
class OpenCodeCheckerReader {

    private static final Duration TIMEOUT = Duration.ofMinutes(20);

    private final HeadlessOpenCode headless;
    private final OpenCodeProperties opencode;
    private final List<RoleDefinition> roles;

    Answer<QaVerdict> read(CheckerReader.Run run) {
        return CheckerAnswers.verdictOf(run, ask(run));
    }

    Answer<QaDecline> reconsider(CheckerReader.Run run) {
        return CheckerAnswers.declineOf(run, ask(run));
    }

    private HeadlessReply ask(CheckerReader.Run run) {
        RoleDefinition.Tools tools = roles.stream().filter(role -> role.id().equals(run.role())).findFirst()
                .orElseThrow(() -> new IllegalStateException("no installed role " + run.role())).tools();
        String model = opencode.readModel(run.role())
                .orElseThrow(() -> new IllegalStateException("no OpenCode model for " + run.role()));
        String prompt = run.shared().isBlank() ? run.prompt() : run.shared() + "\n\n" + run.prompt();
        OpenCodeScope scope = OpenCodeScope.of(tools.shell(), tools.refuseShell(), tools.refuseRead(), tools.fetch(), tools.git(),
                List.of(run.worktree()), run.environments());
        return headless.run(new OpenCodeRead(run.worktree(), prompt, run.schema(), model, scope,
                AssistantCallKind.QA_CHECK, TIMEOUT));
    }
}

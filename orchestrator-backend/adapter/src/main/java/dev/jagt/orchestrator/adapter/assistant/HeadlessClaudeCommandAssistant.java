package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommandAssistant;
import dev.jagt.orchestrator.protocol.CommandRead;
import dev.jagt.orchestrator.task.AssistantCallKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class HeadlessClaudeCommandAssistant implements CommandAssistant {

    /** Mapping text to a command reads nothing and must feel like typing. */
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final HeadlessClaude claude;

    @Override
    public Answer<CommandProposal> mapCommand(String text, String context) {
        if (text == null || text.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "Map this operator request onto EXACTLY ONE command of the tool below.\n\nREQUEST: "
                + text + "\n\n" + context + "\n\nAnswer with the command word, the task it applies to (its"
                + " id or alias, copied verbatim from the list — never invented), the ticket reference for `do` or"
                + " the request URL for `resume`, and a reason. Leave a field as an empty string when it does not"
                + " apply. `do` and `resume` name no task. If the request does not clearly match one command and,"
                + " where it acts on one, one task, answer"
                + " command=\"none\" and put the ambiguity in reason. Do NOT guess between two tasks:"
                + " ambiguity is a `none`. Respond directly.";
        return claude.ask(prompt, CommandRead.SCHEMA.json(), "command mapping", AssistantCallKind.COMMAND_MAP, TIMEOUT)
                .map(n -> new CommandProposal(n.path("command").asString(""), n.path("task").asString(""),
                        n.path("ticket").asString(""), n.path("reason").asString("")));
    }
}

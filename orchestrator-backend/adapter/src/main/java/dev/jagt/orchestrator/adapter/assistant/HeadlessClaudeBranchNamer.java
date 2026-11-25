package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.assistant.BranchNamer;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.task.usage.AssistantCallKind;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeadlessClaudeBranchNamer implements BranchNamer {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static final int PROMPT_MAX = 2000;

  private final ToolLessRead read;

  @Override
  public Answer<String> nameBranch(@Nullable String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return Answer.unavailable();
    }
    String question =
        """
        <role>You name the git branch of one task from the request that opened it.</role>
        <request>%s</request>
        <rules>
        - words: at most four lowercase words saying what the work changes, never the request's \
        opening complaint or context.
        </rules>
        <examples>
        Request "the linter was updated and there are a lot of new warnings, please fix them": \
        words="upgrade linter".
        </examples>
        Respond directly.\
        """
            .formatted(prompt.length() <= PROMPT_MAX ? prompt : prompt.substring(0, PROMPT_MAX));
    return read.ask(
            question,
            Schema.answer().required("words", "string", null).json(),
            "branch name",
            AssistantCallKind.BRANCH_NAME,
            TIMEOUT)
        .map(n -> n.path("words").asString(""));
  }
}

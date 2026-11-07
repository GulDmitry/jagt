package dev.jagt.orchestrator.adapter.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.jagt.orchestrator.protocol.SessionHookReport;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

import java.util.List;

/** The field names Claude's hooks post, bound onto the neutral report. */
abstract class ClaudeHookReportMixIn {

    @SuppressWarnings("unused")
    private ClaudeHookReportMixIn(@JsonProperty("transcript_path") @Nullable String transcriptPath,
                          @JsonProperty("source") @Nullable String source,
                          @JsonProperty("message") @Nullable String message,
                          @JsonUnwrapped SessionHookReport.Ending ending,
                          @JsonUnwrapped SessionHookReport.Turn turn) {
    }

    abstract static class EndingMixIn {

        @SuppressWarnings("unused")
        private EndingMixIn(@JsonProperty("stop_hook_active") @Nullable Boolean continuing,
                            @JsonProperty("error") @Nullable String error,
                            @JsonProperty("reason") @Nullable String reason) {
        }
    }

    abstract static class TurnMixIn {

        @SuppressWarnings("unused")
        private TurnMixIn(@JsonProperty("prompt") @Nullable String prompt,
                          @JsonProperty("tool_name") @Nullable String toolName,
                          @JsonProperty("background_tasks") List<Object> backgroundTasks) {
        }
    }

    static JacksonModule module() {
        return new SimpleModule("claude-hook-report")
                .setMixInAnnotation(SessionHookReport.class, ClaudeHookReportMixIn.class)
                .setMixInAnnotation(SessionHookReport.Ending.class, EndingMixIn.class)
                .setMixInAnnotation(SessionHookReport.Turn.class, TurnMixIn.class);
    }
}

package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.protocol.SessionHookReport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeHookReportMixInTest {

    private final JsonMapper mapper = JsonMapper.builder().addModule(ClaudeHookReportMixIn.module()).build();

    @Test
    void bindsEveryFieldClaudeHooksPostOntoTheNeutralReport() {
        var said = mapper.readValue("""
                {"transcript_path": "/tmp/s.jsonl", "source": "startup", "message": "hi", "stop_hook_active": true,
                 "error": "overloaded", "reason": "clear", "background_tasks": [{"id": "bash-1"}],
                 "prompt": "go on", "tool_name": "Bash", "session_id": "ignored"}""", SessionHookReport.class);

        assertThat(said).isEqualTo(new SessionHookReport("/tmp/s.jsonl", "startup", "hi", true, "overloaded", "clear",
                List.of(Map.of("id", "bash-1")), "go on", "Bash"));
    }

    @Test
    void readsAHookThatSaidNeitherAsAStopWithNothingInFlight() {
        var said = mapper.readValue("{\"stop_hook_active\": false}", SessionHookReport.class);

        assertThat(said.sentOn()).isFalse();
        assertThat(said.pausedOnBackgroundWork()).isFalse();
    }
}

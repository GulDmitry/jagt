package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.protocol.SessionHookReport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeHookJsonTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(ClaudeHookJson.class);

    @Test
    void theContextsMapperReadsAClaudeHookPayloadWhenClaudeIsTheDefaultAgent() {
        runner.run(context -> {
            var said = context.getBean(JsonMapper.class).readValue(
                    "{\"transcript_path\": \"/tmp/s.jsonl\", \"prompt\": \"go on\", \"tool_name\": \"Bash\"}",
                    SessionHookReport.class);

            assertThat(said.transcriptPath()).isEqualTo("/tmp/s.jsonl");
            assertThat(said.prompt()).isEqualTo("go on");
            assertThat(said.toolName()).isEqualTo("Bash");
        });
    }

    @Test
    void bindsNoClaudeFieldNamesWhenAnotherAgentIsConfigured() {
        runner.withPropertyValues("orchestrator.agent.cli=codex")
                .run(context -> assertThat(context).doesNotHaveBean("claudeHookReportModule"));
    }
}

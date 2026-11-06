package dev.jagt.orchestrator.adapter.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "claude", matchIfMissing = true)
class ClaudeHookJson {

    @Bean
    JacksonModule claudeHookReportModule() {
        return ClaudeHookReportMixIn.module();
    }
}

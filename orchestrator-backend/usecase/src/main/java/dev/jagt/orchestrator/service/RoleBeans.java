package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.RoleDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RoleBeans {

    @Bean
    RoleDefinition qaDefinition() {
        return RoleDefinition.QA;
    }

    @Bean
    RoleSettings qaSettings(RoleDefinition qaDefinition, ConfigService config) {
        return new RoleSettings(qaDefinition, config);
    }
}

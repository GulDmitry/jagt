package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.RoleDefinition;
import dev.jagt.orchestrator.config.RoleFiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RoleBeans {

    @Bean
    RoleDefinition qaDefinition() {
        return RoleDefinition.QA;
    }

    @Bean
    RoleFiles qaFiles(RoleDefinition qaDefinition, OrchestratorPaths paths) {
        return new RoleFiles(paths, qaDefinition.id());
    }

    @Bean
    RoleSettings qaSettings(RoleDefinition qaDefinition, ConfigService config) {
        return new RoleSettings(qaDefinition, config);
    }
}

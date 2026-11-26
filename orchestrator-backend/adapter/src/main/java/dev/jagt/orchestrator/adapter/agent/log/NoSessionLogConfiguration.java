package dev.jagt.orchestrator.adapter.agent.log;

import dev.jagt.orchestrator.port.agent.SessionLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class NoSessionLogConfiguration {

  @Bean
  @ConditionalOnMissingBean(SessionLog.class)
  NoSessionLog noSessionLog() {
    return new NoSessionLog();
  }
}

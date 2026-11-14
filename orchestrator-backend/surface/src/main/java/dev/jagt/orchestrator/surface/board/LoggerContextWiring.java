package dev.jagt.orchestrator.surface.board;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LoggerContextWiring {

  @Bean(destroyMethod = "")
  LoggerContext loggerContext() {
    return (LoggerContext) LoggerFactory.getILoggerFactory();
  }
}

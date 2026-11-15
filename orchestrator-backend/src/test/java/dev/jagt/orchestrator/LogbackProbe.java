package dev.jagt.orchestrator;

import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;

public final class LogbackProbe {

  private LogbackProbe() {}

  public static void main(String[] args) {
    MockEnvironment environment =
        new MockEnvironment().withProperty("logging.master.name", args[0] + "/jagt-master.log");
    if (!args[1].isBlank()) {
      environment.addActiveProfile(args[1]);
    }
    System.setProperty("LOG_FILE", args[0] + "/jagt-backend.log");
    LogbackLoggingSystem system = new LogbackLoggingSystem(LogbackProbe.class.getClassLoader());
    system.beforeInitialize();
    system.initialize(
        new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
    LoggerFactory.getLogger("dev.jagt.orchestrator.Probe")
        .atWarn()
        .setMessage("read failed")
        .addKeyValue("url", "http://example.test")
        .addKeyValue("attempt", 2)
        .log();
    system.cleanUp();
  }
}

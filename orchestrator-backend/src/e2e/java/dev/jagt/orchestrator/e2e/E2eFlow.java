package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.terminal.EditorDriver;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("e2e")
@ExtendWith({FakeAssistantDefaults.class, FakeRefusingProcesses.Check.class})
@Import(FakeRefusingProcesses.Wiring.class)
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "orchestrator.agent.cli=stub",
      "orchestrator.terminal=none",
      "orchestrator.startup-checks=false"
    })
@MockitoBean(
    types = {UserNotifier.class, TerminalDriver.class, EditorDriver.class, McpHealth.class})
@interface E2eFlow {

  @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
  SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;
}

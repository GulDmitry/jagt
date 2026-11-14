package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.host.Exit;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessExit implements Exit {

  private static final Duration GRACE = Duration.ofSeconds(2);

  private final ConfigurableApplicationContext context;

  @Override
  public void afterAnswer() {
    log.atInfo().setMessage("jagt exits").addKeyValue("graceMs", GRACE.toMillis()).log();
    Thread.ofPlatform()
        .name("jagt-exit")
        .start(() -> {
          try {
            Thread.sleep(GRACE);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
          System.exit(SpringApplication.exit(context, () -> 0));
        });
  }
}

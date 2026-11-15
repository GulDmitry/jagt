package dev.jagt.orchestrator.adapter.channel;

import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.port.host.Problem;
import dev.jagt.orchestrator.port.host.StartupCheck;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "orchestrator.notifier", havingValue = "none")
public class NoNotifier implements UserNotifier, StartupCheck {

  static final String SENTENCE =
      "orchestrator.notifier is none, so no banner is shown: set `notifier` in jagt.yml to"
          + " `desktop` to get them.";

  @Override
  public void notify(String title, String message, @Nullable String link) {
    log.atInfo()
        .setMessage("banner skipped")
        .addKeyValue("key", "orchestrator.notifier")
        .addKeyValue("cause", "notifier is none")
        .log();
  }

  @Override
  public List<Problem> problems() {
    return List.of();
  }

  @Override
  public List<Problem> notes() {
    return List.of(new Problem("orchestrator.notifier", SENTENCE));
  }

  @Override
  public boolean machine() {
    return false;
  }
}

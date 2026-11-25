package dev.jagt.orchestrator.adapter.agent.log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UnpricedModels {

  private final Set<String> warned = ConcurrentHashMap.newKeySet();

  void warnOnce(@Nullable String model, String fix) {
    if (warned.add(String.valueOf(model))) {
      log.atWarn()
          .setMessage("model unpriced")
          .addKeyValue("model", model)
          .addKeyValue("effect", "cost counted as a lower bound")
          .addKeyValue("fix", fix)
          .log();
    }
  }
}

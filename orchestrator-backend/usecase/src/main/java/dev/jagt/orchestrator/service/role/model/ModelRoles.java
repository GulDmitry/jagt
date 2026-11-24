package dev.jagt.orchestrator.service.role.model;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** The model roles that are running now, whichever the modules declare. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModelRoles {

  private final List<ModelRole> roles;

  public List<String> running() {
    return roles.stream().filter(this::runs).map(ModelRole::id).sorted().toList();
  }

  public boolean runs(String id) {
    return roles.stream().filter(role -> role.id().equals(id)).anyMatch(this::runs);
  }

  private boolean runs(ModelRole role) {
    try {
      return role.running();
    } catch (RuntimeException unread) {
      log.atWarn()
          .setMessage("model role read failed")
          .addKeyValue("role", role.id())
          .addKeyValue("cause", unread.toString())
          .log();
      return false;
    }
  }
}

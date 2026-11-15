package dev.jagt.orchestrator.service.role;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The definition, files and settings of every checker role installed, looked up by role id. */
public final class RoleKits {

  private final Map<String, RoleKit> byId = new LinkedHashMap<>();

  public RoleKits(List<RoleKit> kits) {
    kits.forEach(kit -> byId.put(kit.definition().id(), kit));
  }

  public Optional<RoleKit> find(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  public List<RoleKit> all() {
    return List.copyOf(byId.values());
  }
}

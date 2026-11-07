package dev.jagt.orchestrator.adapter.agent;

import tools.jackson.databind.node.ObjectNode;

public final class OpenCodeProvider {

  private OpenCodeProvider() {}

  static void write(ObjectNode root, OpenCodeProperties opencode) {
    if (opencode.hasEndpoint()) {
      write(root, opencode.endpoint(), opencode.model());
    }
  }

  public static void write(ObjectNode root, String endpoint, String model) {
    ObjectNode local = root.putObject("provider").putObject("local");
    local.put("npm", "@ai-sdk/openai-compatible");
    local.put("name", "local");
    local.putObject("options").put("baseURL", endpoint);
    local.putObject("models").putObject(model);
    root.put("model", "local/" + model);
  }
}

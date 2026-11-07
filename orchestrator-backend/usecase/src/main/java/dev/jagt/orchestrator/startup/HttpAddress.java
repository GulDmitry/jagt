package dev.jagt.orchestrator.startup;

import java.net.URI;

public final class HttpAddress {

  private HttpAddress() {}

  public static boolean is(String address) {
    try {
      URI uri = URI.create(address);
      return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          && uri.getHost() != null;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
